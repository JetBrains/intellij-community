// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test() {
    for(i in 10 downTo 1) {
        if (<caret>i < 1) {}
        if (i <= 1) {}
    }
}
