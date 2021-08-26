// PROBLEM: none
// WITH_RUNTIME
fun test(x: IntArray) {
    x[0] += 1
    if (<caret>x[0] == 1) {}
}