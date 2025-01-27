// "Convert to primary constructor" "true"
// AFTER-WARNING: Parameter 's' is never used
fun log(s: String) {
}

class A {
    var x: String
        get() {
            //comment
            log(field)
            return field
        }

    <caret>constructor(x: String) {
        this.x = x
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFixes$1