// PROBLEM: Condition is always false
// FIX: none
fun test(x : Array<Int>, y : Int?) {
    if (y != null && x[y] > 10) {
        if (<caret>y >= x.size) {}

    }
}
