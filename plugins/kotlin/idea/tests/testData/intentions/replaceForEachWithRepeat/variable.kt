// WITH_STDLIB
fun foo() {
    val n = 10
    (0..<n).<caret>forEach {
        println("Hello")
    }
}