// "Replace with 'this.bar()'" "true"
class A {
    @Deprecated("", ReplaceWith("this.bar()"))
    fun foo() = 1
    fun bar() = 2
}

fun test(){
    A()::foo<caret>
}