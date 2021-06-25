// PROBLEM: Value is always zero
// FIX: none
fun test(x : Int, y: Array<Int>) {
    if (x == 0) {
        other(x)
        y[x] = 1
        y[<caret>x + 1] = 2
    }
}
fun other(x: Int) {}