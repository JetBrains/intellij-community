// "Convert to primary constructor" "true"
// WITH_STDLIB

class WithVarArg {

    val x: List<String>

    constructor(<caret>vararg zz: String) {
        x = listOf(*zz)
    }

    fun foo() {}
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1