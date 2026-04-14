// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(n: Int) {
    (0..n).<caret>forEach {
        println(it)
    }
}