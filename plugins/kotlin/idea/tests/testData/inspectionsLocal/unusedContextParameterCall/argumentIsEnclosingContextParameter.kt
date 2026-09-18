// COMPILER_ARGUMENTS: -Xcontext-parameters
context(p: Int) fun f() {}

context(p: Int)
fun g() {
    <caret>context(p) {
        f()
    }
}