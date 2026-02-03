// WITH_STDLIB
// AFTER-WARNING: Parameter 'arg' is never used
fun fn(arg : () -> Unit) = Unit

fun call() {
    fn { <caret>`callee$`() }
}

fun `callee$`() {}