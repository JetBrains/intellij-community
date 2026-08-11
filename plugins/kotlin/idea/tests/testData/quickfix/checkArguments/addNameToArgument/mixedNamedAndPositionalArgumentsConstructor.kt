// "Add name to argument: 'b = 42'" "true"
// K2_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_ERROR: NO_VALUE_FOR_PARAMETER

class A(a: Int, b: Int, c: Int) {}

fun f() {
     A(c = 1, <caret>42, a = 1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddNameToArgumentFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix