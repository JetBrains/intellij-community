// IS_APPLICABLE: false
// WITH_STDLIB
fun test(xs: List<Int>) {
    xs.isNotEmpty() ||<caret> throw IllegalArgumentException()
}