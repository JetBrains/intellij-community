// WITH_STDLIB
fun foo() {
    <caret>repeat(3) { index ->
        println(index)
    }
}