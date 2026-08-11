// WITH_STDLIB
fun foo() {
     (0..<5).<caret>forEach loop@ {
        if (it == 3) return@loop
        println(it)
    }
}