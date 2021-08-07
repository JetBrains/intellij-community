// PROBLEM: Condition is always false
// FIX: none
fun test(i : Int?) {
    if (i!! > 5) {
        if (<caret>i!! < 3) {

        }
    }
}
