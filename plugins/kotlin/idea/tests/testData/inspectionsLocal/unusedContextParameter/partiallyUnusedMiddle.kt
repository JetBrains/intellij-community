// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String, i: Int)
fun foo() {}

context(s: String, <caret>z: Double, i: Int)
fun test() {
    foo()
}