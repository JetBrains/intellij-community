// "Import" "false"
// ERROR: Unresolved reference: @String
// K2_AFTER_ERROR: UNRESOLVED_LABEL
// K2_ERROR: UNRESOLVED_LABEL

fun refer() {
    val v1 = this@String<caret>
}
