// "Import" "false"
// ERROR: Unresolved reference: @String
// K2_AFTER_ERROR: Unresolved label.

fun refer() {
    val v1 = this@String<caret>
}
