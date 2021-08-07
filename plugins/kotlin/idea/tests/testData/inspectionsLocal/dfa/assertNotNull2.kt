// PROBLEM: Operation will always fail as operand is always null
// FIX: none
fun test(i : Int?) {
    if (i == null) {
        i!<caret>!
    }
}
