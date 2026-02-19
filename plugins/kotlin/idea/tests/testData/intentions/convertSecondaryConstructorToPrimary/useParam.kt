// "Convert to primary constructor" "true"
class UseParam {
    constructor<caret>(x: Int) {
        this.y = x
    }

    val y: Int
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1