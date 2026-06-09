// "Convert to primary constructor" "true"
annotation class AnnParam

annotation class AnnProperty

abstract class WithComposedModifiers {
    @AnnProperty
    open var x: Array<out String> = emptyArray()

    constructor<caret>(@AnnParam vararg x: String) {
        //comment1
        this.x = x
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1