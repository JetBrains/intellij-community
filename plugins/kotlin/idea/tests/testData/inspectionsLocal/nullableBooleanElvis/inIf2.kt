// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun foo() {
    var a: Boolean? = null
    if (!(a <caret>?: false)) {

    }
}