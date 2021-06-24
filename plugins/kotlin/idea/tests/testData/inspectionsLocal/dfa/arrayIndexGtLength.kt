// PROBLEM: Array index is out of bounds
// FIX: none
fun test(x : Array<Int>, y : Int) {
    if (y >= x.size) {
        if (x[<caret>y] > 10) {
        }
    }
}
