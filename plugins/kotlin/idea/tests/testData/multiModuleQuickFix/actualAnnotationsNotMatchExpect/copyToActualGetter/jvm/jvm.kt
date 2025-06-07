// "Copy mismatched annotation 'Ann' from 'expect' to 'actual' declaration (may change semantics)" "true"
// DISABLE_ERRORS
// FIR_COMPARISON

actual val foo<caret>: Any?
    get() = null
