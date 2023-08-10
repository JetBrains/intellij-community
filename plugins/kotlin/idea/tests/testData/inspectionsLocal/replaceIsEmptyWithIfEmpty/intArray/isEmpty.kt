// PROBLEM: none
// WITH_STDLIB
fun test(intArr: IntArray): IntArray {
    return if (intArr.isEmpty<caret>()) {
        intArrayOf(1)
    } else {
        intArr
    }
}