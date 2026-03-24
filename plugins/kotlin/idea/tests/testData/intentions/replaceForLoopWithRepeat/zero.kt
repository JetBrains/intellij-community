// WITH_STDLIB
fun foo() {
    <caret>for (it in 0..<0) {
        println("Never")
    }
}