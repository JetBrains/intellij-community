// PROBLEM: Condition is always false
// FIX: none
fun test(arr : Array<Int>) {
    for (x in arr) {
        if (x > 5 && <caret>x < 3) {}
    }
}