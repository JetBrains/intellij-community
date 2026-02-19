// WITH_STDLIB
fun foo() {
    <caret>repeat(0) {
        println("Never")
    }
}