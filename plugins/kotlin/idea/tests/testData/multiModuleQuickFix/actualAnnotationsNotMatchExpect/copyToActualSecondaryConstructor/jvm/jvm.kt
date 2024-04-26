// "Copy mismatched annotation 'Ann' from 'expect' to 'actual' declaration (may change semantics)" "true"
// DISABLE-ERRORS
// FIR_COMPARISON

actual class Foo actual constructor(p: Any?) {
  actual constructor<caret>() : this(null)
}
