// "Convert to primary constructor" "true"
class Protected {
    internal var s: String

    protected <caret>constructor(s: String) {
        this.s = s
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1