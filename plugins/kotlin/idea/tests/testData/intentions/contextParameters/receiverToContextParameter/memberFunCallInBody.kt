// COMPILER_ARGUMENTS: -Xcontext-parameters

fun <caret>Bar.foo() {
    bar()
}

class Bar {
    fun bar() {}
}
