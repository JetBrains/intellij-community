// PROBLEM: Condition is always true
// FIX: none
fun test(x: Int) {
    var y = 1
    do {
        ++y
    } while(y < x)
    if (<caret>y >= x) {}
}