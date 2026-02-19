// WITH_STDLIB
// IS_APPLICABLE: false
fun foo() {
    <caret>repeat(3) { _ ->
        println("ignored")
    }
}