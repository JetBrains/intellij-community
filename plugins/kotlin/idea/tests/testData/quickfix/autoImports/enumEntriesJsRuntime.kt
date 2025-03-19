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
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix