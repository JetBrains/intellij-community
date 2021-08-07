// PROBLEM: Condition is always false
// FIX: none
fun test(obj : Int) {
    if (obj in 1..10) {
        if (<caret>obj !in 0..11) {}
    }
}
