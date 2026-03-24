// WITH_STDLIB
fun foo(x: Int) {
    <caret>for (it in 0..<x + 1) {
        println(it)
    }
}