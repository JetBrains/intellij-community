// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test() {
    for(i in 1 until 10) {
        if (<caret>i >= 10) {}
    }
}
