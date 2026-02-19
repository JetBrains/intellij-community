// "Convert to primary constructor" "true"
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
class WithParameters {
    constructor(<caret>x: Int = 42, y: String = "Hello")
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1