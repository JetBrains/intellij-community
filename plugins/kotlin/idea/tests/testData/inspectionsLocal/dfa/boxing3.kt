// PROBLEM: Condition is always false
// FIX: none
fun test(y : Int, z: Int) {
    var x : Int? = null
    if (z == 2) x = y
    if (z == 2 && <caret>x == null) {}
}