// "Convert to primary constructor" "true"
class ConvertToInit {
    fun foo() {}

    fun bar() {}

    constructor(<caret>) {
        foo()
        //comment1
        bar()
        //comment2
    }

    fun baz() {}
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1