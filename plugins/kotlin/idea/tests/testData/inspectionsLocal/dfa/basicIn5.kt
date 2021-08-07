// PROBLEM: Condition is always false
// FIX: none
fun test(obj : Int) {
    if (obj in 20..30) {
        if (<caret>obj in 1..10) {}
    }
}
