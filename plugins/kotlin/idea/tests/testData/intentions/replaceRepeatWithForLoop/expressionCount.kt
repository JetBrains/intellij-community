// WITH_STDLIB
fun foo(x: Int) {
    <caret>repeat(x + 1) {
        println(it)
    }
}