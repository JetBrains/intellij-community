// WITH_STDLIB
fun foo() {
    (0..<5).<caret>forEach {
        if (it == 3) return@forEach
        println(it)
    }
}