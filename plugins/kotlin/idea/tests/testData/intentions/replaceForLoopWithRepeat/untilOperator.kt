// WITH_STDLIB
fun foo() {
    <caret>for (it in 0 until 5) {
        println(it)
    }
}