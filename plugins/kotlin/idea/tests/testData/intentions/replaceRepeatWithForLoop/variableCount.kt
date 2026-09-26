// WITH_STDLIB
fun foo() {
    val n = 10
    <caret>repeat(n) {
        println("Hello")
    }
}