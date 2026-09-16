// "Import class 'Arrays'" "true"
// DISABLE_ERRORS

fun test() {
    val java = 42
    Arrays<caret>
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix