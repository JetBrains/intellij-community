// "Replace with 'this.bar()'" "true"
@Deprecated("", ReplaceWith("this.bar()"))
fun <T> T.foo() {
}

fun <T> T.bar() {
}

fun <T> test(i: Int) {
    i.<caret>foo()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix