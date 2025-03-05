// "class org.jetbrains.kotlin.idea.quickfix.ChangeFunctionSignatureFix" "false"
// ERROR: No value passed for parameter 'other'
// K2_AFTER_ERROR: No value passed for parameter 'other'.

fun f(d: Boolean) {
    d.or(<caret>)
}
