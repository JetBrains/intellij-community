// HIGHLIGHT: GENERIC_ERROR_OR_WARNING

fun foo() {
    var a: Boolean? = null
    when {
        a <caret>?: false -> {}
    }
}