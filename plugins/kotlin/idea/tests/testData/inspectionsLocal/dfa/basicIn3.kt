// PROBLEM: Condition is always false
// FIX: none
fun test(obj : Int) {
    if (1 in obj..10) {
        if (<caret>obj > 1) {}
    }
}
