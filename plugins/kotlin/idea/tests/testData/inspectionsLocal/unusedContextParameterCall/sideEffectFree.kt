// COMPILER_ARGUMENTS: -Xcontext-parameters
fun foo() {}

fun test() {
    val s = "hello"
    <caret>context(s) {
        foo()
    }
}