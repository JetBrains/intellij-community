// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test(a: Int, b: Int) {
    val (c, d) = a to b
    if (c > d) {
        if (<caret>c < d) {

        }
    }
}
