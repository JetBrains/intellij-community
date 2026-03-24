// WITH_STDLIB
fun foo() {
    <caret>repeat(3) { /* iteration */ index ->
        println(index) // print index
    }
}