// "Convert to primary constructor" "true"
class WithDifferentTypeProperty {
    val x: Number

    val y: String

    constructor(x: Int, <caret>z: String) {
        this.x = x
        this.y = z
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFixes$1