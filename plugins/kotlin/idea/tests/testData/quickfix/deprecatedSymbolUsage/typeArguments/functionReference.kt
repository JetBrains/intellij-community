// "Replace with 'bar()'" "true"
class A {
    @Deprecated("", ReplaceWith("bar()"))
    fun foo() = 1
    fun bar() = 2
}

fun test(){
    A()::foo<caret>
}