// WITH_STDLIB
fun foo() {
    val n = 10
    <caret>for (it in 0..<n) {
        println("Hello")
    }
}