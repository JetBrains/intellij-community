// "Replace arguments of mismatched annotation 'Ann' on 'actual' declaration (may change semantics)" "true"
// DISABLE_ERRORS
// FIR_COMPARISON

@Ann(p2 = "different", p1 = "different")
actual fun foo<caret>() {}
