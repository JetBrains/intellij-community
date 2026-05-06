// "Add name to argument: 'c = false'" "true"
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: No value passed for parameter 'c'.

fun foo(a: Int?, b: String?, c: Boolean) {}

fun bar() {
    foo(b = "foo", a = 2, <caret>false)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddNameToArgumentFix