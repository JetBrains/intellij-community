// PROBLEM: none
// WITH_RUNTIME
fun test(x : Array<Int>?) {
    if (<caret>x?.size ?: 0 > 0) {

    }
}