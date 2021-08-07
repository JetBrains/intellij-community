// PROBLEM: Condition is always true
// FIX: none
// WITH_RUNTIME
fun test(x: Int) {
    val result = try { 100 / x } catch(ex: ArithmeticException) { 200 }
    if (result == 200) {
        if (<caret>x == 0) {}
    }
}
