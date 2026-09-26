// PROBLEM: none
// WITH_STDLIB
fun test(intArr: IntArray): IntArray {
    return <caret>if (intArr.isNotEmpty()) {
        intArr
    } else {
        intArrayOf(1)
    }
}