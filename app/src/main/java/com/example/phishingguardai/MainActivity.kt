package com.example.phishingguardai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private val DarkBackground = Color(0xFF080B10)
private val CardBackground = Color(0xFF151B25)
private val Mint = Color(0xFF50F0C0)
private val Red = Color(0xFFFF5263)
private val WhiteText = Color(0xFFF4F7FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhishingGuardApp()
        }
    }
}

@Composable
fun PhishingGuardApp() {
    val context = LocalContext.current

    var url by remember { mutableStateOf("") }
    var scanned by remember { mutableStateOf(false) }
    var suspicious by remember { mutableStateOf(false) }
    var riskScore by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf("Enter a link to begin scanning.") }

    val preferences = remember {
        context.getSharedPreferences("phishing_guard", Context.MODE_PRIVATE)
    }

    var history by remember {
        mutableStateOf(
            preferences.getString("history", "")
                ?.lines()
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    fun scanLink(input: String) {
        val link = input.trim()
        url = link
        scanned = true

        if (link.isBlank() ||
            !Patterns.WEB_URL.matcher(link).matches()
        ) {
            suspicious = true
            riskScore = 100
            result = "Invalid link. Check the address before opening it."
        } else {
            val lower = link.lowercase()
            val host = Uri.parse(
                if (lower.startsWith("http")) lower
                else "https://$lower"
            ).host.orEmpty()

            var score = 0

            if (!lower.startsWith("https://")) score += 20

            if (link.contains("@")) score += 25

            val warningWords = listOf(
                "verify", "password", "claim-prize",
                "free-gift", "urgent", "login",
                "bank-alert", "winner", "account-suspend"
            )

            if (warningWords.any { lower.contains(it) }) {
                score += 25
            }

            if (host.count { it == '-' } >= 2) score += 10

            val shorteners = listOf(
                "bit.ly", "tinyurl.com", "t.co",
                "shorturl.at", "is.gd"
            )

            if (shorteners.any { host.contains(it) }) {
                score += 20
            }

            if (Regex("""\d{1,3}(\.\d{1,3}){3}""")
                    .containsMatchIn(host)
            ) {
                score += 25
            }

            riskScore = score.coerceIn(0, 100)
            suspicious = riskScore >= 35

            result = if (suspicious) {
                "Warning: this link has suspicious characteristics. Do not enter passwords or banking details."
            } else {
                "No obvious warning signs were detected. This does not guarantee the website is safe."
            }
        }

        val entry = "${if (suspicious) "WARNING" else "CHECKED"} | $riskScore/100 | $link"
        history = (listOf(entry) + history).take(10)

        preferences.edit()
            .putString("history", history.joinToString("\n"))
            .apply()
    }

    fun openPortal() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://cybercrime.gov.in/")
        )
        context.startActivity(intent)
    }

    fun copyLink() {
        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("Suspicious link", url)
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DarkBackground,
            surface = CardBackground,
            primary = Mint,
            secondary = Red,
            onBackground = WhiteText,
            onSurface = WhiteText
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PHISHING GUARD AI",
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                color = Mint
            )

            Text(
                text = "SMART LINK SECURITY",
                color = WhiteText,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(22.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CardBackground
                )
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "SCAN A LINK",
                        color = Mint,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Paste website URL") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { scanLink(url) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SCAN LINK")
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            GmsBarcodeScanning.getClient(context)
                                .startScan()
                                .addOnSuccessListener { barcode ->
                                    barcode.rawValue?.let {
                                        url = it
                                        scanned = false
                                        result = "QR link loaded. Tap SCAN LINK to check it."
                                    }
                                }
                                .addOnFailureListener {
                                    result = "QR scanner failed. Please try again."
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SCAN QR CODE")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (scanned) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = CardBackground
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (suspicious) {
                            Image(
                                painter = painterResource(
                                    id = R.drawable.warning_cat
                                ),
                                contentDescription = "Suspicious link warning",
                                modifier = Modifier.size(100.dp)
                            )

                            Text(
                                "SUSPICIOUS LINK",
                                color = Red,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Image(
                                painter = painterResource(
                                    id = R.drawable.safe_shield
                                ),
                                contentDescription = "Link safety check",
                                modifier = Modifier.size(100.dp)
                            )

                            Text(
                                "NO OBVIOUS WARNING SIGNS",
                                color = Mint,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Risk score: $riskScore / 100",
                            color = if (suspicious) Red else Mint,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { riskScore / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (suspicious) Red else Mint,
                            trackColor = Color.DarkGray
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(result, color = WhiteText)

                        Spacer(Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { copyLink() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("COPY LINK")
                        }

                        Button(
                            onClick = { openPortal() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Red
                            )
                        ) {
                            Text("REPORT / GET CYBER HELP")
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionCard("SCAN HISTORY") {
                if (history.isEmpty()) {
                    Text("Your recent scans will appear here.")
                } else {
                    history.forEach { item ->
                        Text(item, color = WhiteText)
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = Color.DarkGray
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            history = emptyList()
                            preferences.edit()
                                .remove("history")
                                .apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLEAR HISTORY")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard("EMERGENCY SECURITY GUIDE") {
                EmergencyItem(
                    "1. Block your bank card",
                    "Use your bank's official app or website, or call the number printed on your card."
                )

                EmergencyItem(
                    "2. Secure your UPI",
                    "Contact your bank or payment app through its official support channel if you suspect compromise."
                )

                EmergencyItem(
                    "3. Protect your accounts",
                    "From a trusted device, change compromised passwords and enable two-factor authentication."
                )

                EmergencyItem(
                    "4. Report financial cyber fraud",
                    "Call 1930 in India as soon as possible if money has been lost to financial cyber fraud."
                )

                EmergencyItem(
                    "5. Preserve evidence",
                    "Keep transaction IDs, messages, screenshots and suspicious URLs. Never share your OTP or PIN."
                )

                Button(
                    onClick = { openPortal() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Red
                    )
                ) {
                    Text("OPEN OFFICIAL CYBERCRIME PORTAL")
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Phishing Guard AI • Hackronix 2026",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Text(
                "Risk scores are heuristic estimates, not proof of safety.",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SectionCard(
    heading: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = {
                Text(
                    heading,
                    color = Mint,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                content()
            }
        )
    }
}

@Composable
fun EmergencyItem(title: String, description: String) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(
            title,
            color = Mint,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        Text(description, color = WhiteText)
    }
}