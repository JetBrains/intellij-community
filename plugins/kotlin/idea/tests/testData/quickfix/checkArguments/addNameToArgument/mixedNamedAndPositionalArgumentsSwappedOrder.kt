// "Add name to argument: 'c = false'" "true"
// K2_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_ERROR: NO_VALUE_FOR_PARAMETER

fun foo(a: Int?, b: String?, c: Boolean) {}

fun bar() {
    foo(b = "foo", a = 2, <caret>false)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddNameToArgumentFix