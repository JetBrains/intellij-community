// WITH_STDLIB
fun foo() {
    (0..<5).<caret>forEach {
        println(it)
    }
}