// PROBLEM: Condition is always false
// FIX: none
fun test(x : Int) : Boolean {
    if (x % 2 == 0) {
        return true;
    }
    return x <caret>== 10;
}