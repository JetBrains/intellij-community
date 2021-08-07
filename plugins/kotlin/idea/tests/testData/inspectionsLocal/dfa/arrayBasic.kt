// PROBLEM: Condition is always false
// FIX: none
fun test(x : Array<Int>) {
    if (x[0] > 10)
        if (<caret>x[0] < 0) {

    }
}
