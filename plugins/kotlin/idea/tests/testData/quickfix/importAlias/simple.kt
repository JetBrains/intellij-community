// "Introduce import alias" "true"
// WITH_RUNTIME
// ACTION: Introduce import alias
fun foo() {
    listOf("a", "b", "c").<caret>forEach { }
}