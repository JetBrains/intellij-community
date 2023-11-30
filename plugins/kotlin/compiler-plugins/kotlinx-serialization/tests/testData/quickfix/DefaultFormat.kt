// "Replace with default Json format instance" "true"
import kotlinx.serialization.*
import kotlinx.serialization.json.*

fun foo() {
    <caret>Json {}.encodeToString(Any())
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.quickfixes.JsonRedundantDefaultQuickFix