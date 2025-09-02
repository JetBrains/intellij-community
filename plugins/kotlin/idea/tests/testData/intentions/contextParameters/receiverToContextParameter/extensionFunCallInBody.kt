// COMPILER_ARGUMENTS: -Xcontext-parameters

fun <caret>String.foo() {
    bar()
}

fun String.bar() {
}