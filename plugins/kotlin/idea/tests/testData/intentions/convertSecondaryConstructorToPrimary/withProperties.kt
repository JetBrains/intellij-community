// "Convert to primary constructor" "true"
class WithProperties {
    val x: Int
    val y: Int
    private val z: Int

    constructor(x: Int, y: Int = 7, z: Int = 13<caret>) {
        this.x = x
        this.y = y
        this.z = z
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1