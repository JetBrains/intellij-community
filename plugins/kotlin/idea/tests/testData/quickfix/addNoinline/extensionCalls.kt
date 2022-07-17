// "Add 'noinline' to parameter 'lambda'" "true"
// WITH_STDLIB

inline fun inlineFun(lambda: () -> Unit) {
    <caret>lambda.let { }
}
