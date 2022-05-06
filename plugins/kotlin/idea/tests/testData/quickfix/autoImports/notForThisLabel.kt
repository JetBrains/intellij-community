// "Import" "false"
// ACTION: Do not show return expression hints
// ERROR: Unresolved reference: @String

fun refer() {
    val v1 = this@String<caret>
}
