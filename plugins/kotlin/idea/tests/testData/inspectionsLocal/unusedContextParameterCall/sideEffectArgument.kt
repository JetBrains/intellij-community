// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
fun makeString(): String = ""
fun foo() {}

fun test() {
    <caret>context(makeString()) {
        foo()
    }
}