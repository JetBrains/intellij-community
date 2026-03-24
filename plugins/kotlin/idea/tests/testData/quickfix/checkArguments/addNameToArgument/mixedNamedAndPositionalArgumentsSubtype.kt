// "Add name to argument: 'b = B()'" "true"
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: No value passed for parameter 'b'.

open class A {}
open class B : A() {}

fun f(a: A, b: A, c: A) {}
fun g() {
     f(c=A(), <caret>B(), a=A())
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddNameToArgumentFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix