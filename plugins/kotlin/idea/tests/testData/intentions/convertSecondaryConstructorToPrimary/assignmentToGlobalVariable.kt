// "Convert to primary constructor" "true"
var state: String = "Fail"

class A {
    val p: String

    <caret>constructor(x: String) {
        p = x
        state = x
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFixes$1