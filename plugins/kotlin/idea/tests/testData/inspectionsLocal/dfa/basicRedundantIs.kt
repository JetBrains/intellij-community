// PROBLEM: none
// WITH_RUNTIME
fun test() {
    val x: Int = 1
    @Suppress("USELESS_IS_CHECK")
    if (<caret>x is Int) {}
}
