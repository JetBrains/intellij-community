// PROBLEM: Condition is always true
// FIX: none
fun test(x : Int) {
    var a : Int
    a = 3
    if (x > 0) {
        a += 2
    } else {
        a *= 3
    }
    if (a == 5 || a <caret>== 9) {}
    var b : Boolean
    b = true
}