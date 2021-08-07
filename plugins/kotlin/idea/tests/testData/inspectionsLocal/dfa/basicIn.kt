// PROBLEM: Condition is always false
// FIX: none
fun test(obj : Int) {
    if (obj > 10) {
        if (<caret>obj in 1..5) {}
    }
}
