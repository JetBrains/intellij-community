// "Replace arguments of mismatched annotation 'Ann' on 'expect' declaration (may change semantics)" "true"
// DISABLE_ERRORS
// FIR_COMPARISON

@Ann("different value")
actual fun foo<caret>() {}
