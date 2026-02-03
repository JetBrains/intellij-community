// MOVE: left
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class A {
    context(b: Int, <caret>a: Int, c: Int)
    fun foo() {
    }
}
// IGNORE_K1