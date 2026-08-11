// WITH_STDLIB
fun foo(x: Int) {
    (0..<x + 1).<caret>forEach {
        println(it)
    }
}