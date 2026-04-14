// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(xs: List<Int>) {
    xs.<caret>forEach {
        println(it)
    }
}