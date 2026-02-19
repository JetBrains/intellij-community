// PROBLEM: none
// WITH_STDLIB
fun test(intArr: IntArray): IntArray {
    return <caret>if (intArr.isEmpty()) {
        intArrayOf(1)
    } else {
        intArr
    }
}