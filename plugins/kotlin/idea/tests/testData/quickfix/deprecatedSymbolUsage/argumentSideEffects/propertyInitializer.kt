// "Replace with 'this.otherProperty'" "true"
// WITH_STDLIB
class A {
    @Deprecated("", ReplaceWith("this.otherProperty"))
    val property: Int = 0
    val otherProperty: Int by lazy { callMethod() }

    private fun callMethod(): Int {
        TODO("Not yet implemented updated")
    }
}

fun client() {
    A().prop<caret>erty
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix