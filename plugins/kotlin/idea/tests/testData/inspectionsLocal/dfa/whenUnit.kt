// PROBLEM: Condition is always false
// FIX: none
fun test(x: Int): Unit {
    when {
        x > 10 -> return
        x < 0 -> return
    }
    if (<caret>x == 11) {}
}
