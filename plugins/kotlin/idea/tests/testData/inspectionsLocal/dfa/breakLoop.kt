// PROBLEM: Condition is always false
// FIX: none
fun test() {
    var x = 0
    while (true) {
        x++
        if (x > 5) break
    }
    if (<caret>x < 6) {

    }
}