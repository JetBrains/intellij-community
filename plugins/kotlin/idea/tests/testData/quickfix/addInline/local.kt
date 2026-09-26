// "Add 'inline' to function 'foo'" "false"
// ACTION: Enable a trailing comma by default in the formatter
// ERROR: Modifier 'crossinline' is allowed only for function parameters of an inline function
// K2_AFTER_ERROR: ILLEGAL_INLINE_PARAMETER_MODIFIER
// K2_ERROR: ILLEGAL_INLINE_PARAMETER_MODIFIER

fun bar() {
    fun foo(<caret>crossinline body: () -> Unit) {

    }
}
