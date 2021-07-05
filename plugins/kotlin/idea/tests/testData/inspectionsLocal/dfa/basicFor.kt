// PROBLEM: Condition is always false
// FIX: none
fun test() {
    for(i in 1..10) {
        if (<caret>i < 1) {}
    }
}
