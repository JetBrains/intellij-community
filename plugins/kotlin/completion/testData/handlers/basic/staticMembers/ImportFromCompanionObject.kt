import kotlin.text.Regex.Companion.fromLiteral

fun foo() {
    escape<caret>
}

// IGNORE_K2
// INVOCATION_COUNT: 1
// ELEMENT_TEXT: "Regex.escapeReplacement"
