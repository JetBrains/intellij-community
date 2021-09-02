// PROBLEM: Value is always zero
// FIX: none
// WITH_RUNTIME
fun test(x: MutableList<Int>, z: Int) {
    if (z == 0) {
        x[0] = <caret>z - 1
    }
}