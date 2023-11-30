// "Replace arguments of mismatched annotation 'Ann' on 'expect' declaration (may change semantics)" "true"
// DISABLE-ERRORS
// FIR_COMPARISON

@Ann("different value")
actual fun foo<caret>() {}
