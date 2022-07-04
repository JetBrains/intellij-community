// "Add 'noinline' to parameter 'lambda'" "true"
// WITH_STDLIB
fun main() {
    inlineFun { }
}

inline fun inlineFun(lambda: () -> Unit) {
    <caret>lambda.let { }
}
