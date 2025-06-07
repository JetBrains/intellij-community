// "Convert to primary constructor" "true"
// WITH_STDLIB

class VarargVal {
    val param: Array<out String>

    constructor<caret>(vararg param: String) {
        this.param = param
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1