// PROBLEM: Condition is always true
// FIX: none
fun test(x : Array<Int>, y : Int) {
    if (x[y] > 10) {
        if (<caret>y >= 0) {}

    }
}
