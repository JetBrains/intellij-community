// IS_APPLICABLE: false
// WITH_STDLIB
fun foo() {
    <caret>for (it in 0..5) {
        println(it)
    }
}