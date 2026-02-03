// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c: Int)
fun <caret>String.foo(param: String) {
}

context(c1: Int)
fun String.bar() {
    foo("boo")
}
