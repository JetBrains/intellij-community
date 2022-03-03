// IS_APPLICABLE: false
// WITH_STDLIB
fun test(i: Int?): IntArray? {
    return i?.let { <caret>intArrayOf(it) }
}