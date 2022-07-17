// PROBLEM: none
// WITH_STDLIB
fun test(intArr: IntArray): IntArray {
    return if (intArr.isNotEmpty<caret>()) {
        intArr
    } else {
        intArrayOf(1)
    }
}