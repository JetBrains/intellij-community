// "Convert to primary constructor" "true"
annotation class Ann

internal class WithModifiers {
    @Ann
    private constructor<caret>()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1