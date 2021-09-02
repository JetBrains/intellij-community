// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test(x : Int) {
    if (x > 5) {
        fail(x)
    }
    if (<caret>x == 10) {}
}
fun fail(value: Int) : Nothing {
    throw RuntimeException("Oops: ${value}")
}