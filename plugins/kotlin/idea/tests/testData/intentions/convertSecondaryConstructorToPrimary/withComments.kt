// "Convert to primary constructor" "true"
class WithComments {
    /**
     * A very important property
     */
    val veryImportant: Any

    /**
     * A constructor
     */
    constructor<caret>(/* Some parameter */veryImportant: Any) {
        this.veryImportant = veryImportant
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFixes$1