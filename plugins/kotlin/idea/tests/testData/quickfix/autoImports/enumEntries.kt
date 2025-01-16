// "Import class 'BLUE'" "true"
package e

enum class ImportEnum {
    RED, GREEN, BLUE
}

val v5 = <caret>BLUE
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// IGNORE_K2