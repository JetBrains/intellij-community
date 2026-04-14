// WITH_STDLIB
fun foo() {
    (0 until 5).<caret>forEach {
        println(it)
    }
}