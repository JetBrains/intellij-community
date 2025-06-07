// "Remove mismatched annotation 'Ann' from 'expect' declaration (may change semantics)" "true"
// DISABLE_ERRORS
// FIR_COMPARISON

actual typealias CommonSynchronized<caret> = kotlin.jvm.Synchronized
