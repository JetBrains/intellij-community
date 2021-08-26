// PROBLEM: Value is always zero
// FIX: none
// WITH_RUNTIME
fun test(x: MutableList<Int>, z: Int, y: Int) {
    if (z == 0) {
        x[y] = <caret>z - 1
    }
}