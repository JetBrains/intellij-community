// "Replace with 'newFun(this, value)'" "true"
class Foo {
    @Deprecated(message = "", replaceWith = ReplaceWith("newFun(this, value)"))
    fun String.oldFun(value: String) {}

    fun newFun(key: String, value: String) {}
}

fun foo(init: Foo.() -> Unit) {}

fun test() {
    foo {
        "a".<caret>oldFun("b")
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix