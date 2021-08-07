// PROBLEM: Index is always out of bounds
// FIX: none
fun test(x : Array<Int>, y : Int?) {
    if (y != null && y < 0) {
        if (x[<caret>y] > 10) {

        }
    }
}
