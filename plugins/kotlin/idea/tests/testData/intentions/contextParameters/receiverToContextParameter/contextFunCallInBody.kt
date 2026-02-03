// COMPILER_ARGUMENTS: -Xcontext-parameters

fun <caret>String.foo() {
    bar()
}

context(c: String)
fun bar() {
}