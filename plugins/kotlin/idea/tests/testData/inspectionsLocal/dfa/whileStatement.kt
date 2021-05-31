// PROBLEM: Condition is always false
// FIX: none
fun test() {
    var x = 0
    while (x < 100000) {
        x += 2
        if (x <caret>== 11) {}
    }
}