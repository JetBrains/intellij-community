// IS_APPLICABLE: false
// WITH_STDLIB
fun test(xs: List<Int>) {
    (throw IllegalArgumentException()) ||<caret> xs.isNotEmpty()
}
