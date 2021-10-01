// PROBLEM: Condition is always true
// FIX: none
fun test() {
    var x = 0
    while (true) {
        ++x
        if (x > 5) continue
        if (<caret>x < 6) {

        }
    }
    if (x < 6) {

    }
}