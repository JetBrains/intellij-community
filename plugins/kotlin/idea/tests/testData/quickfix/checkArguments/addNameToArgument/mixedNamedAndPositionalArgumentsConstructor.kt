// "Add name to argument: 'b = 42'" "true"

class A(a: Int, b: Int, c: Int) {}

fun f() {
     A(c = 1, <caret>42, a = 1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddNameToArgumentFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix