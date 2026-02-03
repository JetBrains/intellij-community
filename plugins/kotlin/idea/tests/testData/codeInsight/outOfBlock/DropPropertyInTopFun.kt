// OUT_OF_CODE_BLOCK: FALSE
// TYPE: "\b\b\b\b\b\b\b\b\b"

fun dropProperty() {
    val x = 1<caret>
    if (x == 0) {
    }
}
// RESOLVE_REF_AFTER: "x"
// ERROR: Unresolved reference: x
