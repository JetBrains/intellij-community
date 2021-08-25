// PROBLEM: none
// WITH_RUNTIME
fun test() {
    val x: Int = 1
    @Suppress("SENSELESS_COMPARISON")
    if (<caret>x == null) {}
}
