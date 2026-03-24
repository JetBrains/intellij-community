// IS_APPLICABLE: false
// WITH_STDLIB
fun foo() {
    <caret>for (it in 1..<5) {
        println(it)
    }
}