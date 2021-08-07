// PROBLEM: Value is always zero
// FIX: none
fun test(x:Int) {
    if (x == 0) {
        val y = <caret>x + 1
    }
}