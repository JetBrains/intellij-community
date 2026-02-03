// "Remove variable 'a' (may change semantics)" "true"
fun test() {
    val <caret>a: (String) -> Unit = { s: String -> s + s }
}
