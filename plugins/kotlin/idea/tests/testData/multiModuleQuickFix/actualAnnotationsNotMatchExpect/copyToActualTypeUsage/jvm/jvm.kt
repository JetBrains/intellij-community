// "Copy mismatched annotation 'Ann' from 'expect' to 'actual' declaration (may change semantics)" "true"
// IGNORE_K1
// DISABLE_ERRORS
// FIR_COMPARISON
// Diagnostic reporting on types is supported only starting from K2

actual fun foo<caret>(p: Any) {}
