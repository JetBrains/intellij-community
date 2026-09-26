// COMPILER_ARGUMENTS: -Xcontext-parameters
// NEW_NAME: ctx

fun <caret>String.foo() {
    bar()
}

context(c: String)
fun bar() {
}