// "Import enum entry 'ImportEnum.BLUE'" "true"
// K2_ERROR: Unresolved reference 'BLUE'.
package e

enum class ImportEnum {
    RED, GREEN, BLUE
}

val v5 = <caret>BLUE
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix