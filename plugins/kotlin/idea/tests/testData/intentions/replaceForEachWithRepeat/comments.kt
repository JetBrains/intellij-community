// WITH_STDLIB
fun foo() {
    // before
    (0..<3).<caret>forEach {
        /* inside */
        println(it)
    }
}