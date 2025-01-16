// "Import" "true"
package e

enum class ImportEnum {
    RED, GREEN, BLUE
}

class ImportClass {
    companion object {
        val BLUE = 0
    }
}

val v5 = <caret>BLUE
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// IGNORE_K2