// AFTER-WARNING: No cast needed
fun main() {
    foo()
    <caret>({ foo() } as? () -> Unit)
}

fun foo() {}
