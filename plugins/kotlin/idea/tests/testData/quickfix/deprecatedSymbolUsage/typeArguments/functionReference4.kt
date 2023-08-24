// "Replace with 'this.bar()'" "true"
class A

@Deprecated("", ReplaceWith("this.bar()"))
fun A.foo() = 1
fun A.bar() = 2

fun test(a: A) {
    a::foo<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix