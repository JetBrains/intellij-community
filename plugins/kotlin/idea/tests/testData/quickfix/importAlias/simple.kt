// "Introduce import alias" "true"
// WITH_STDLIB
// ACTION: Introduce import alias
fun foo() {
    listOf("a", "b", "c").<caret>forEach { }
}