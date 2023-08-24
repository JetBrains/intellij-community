// "Replace with 'bar()'" "true"
class A {
    @Deprecated("", ReplaceWith("bar()"))
    fun foo() = 1
    fun bar() = 2
}

fun test(){
    A()::foo<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix