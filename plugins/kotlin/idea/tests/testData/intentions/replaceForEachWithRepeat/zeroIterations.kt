// WITH_STDLIB
fun foo() {
    (0..<0).<caret>forEach {
        println("Never")
    }
}