// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(n: Long) {
    (0 until n).<caret>forEach {
        println(it)
    }
}