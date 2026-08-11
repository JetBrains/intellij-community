// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(n: Int) {
    (1..<n).<caret>forEach {
        println(it)
    }
}