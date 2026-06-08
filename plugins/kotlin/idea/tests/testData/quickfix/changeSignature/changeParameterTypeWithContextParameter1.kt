
// COMPILER_ARGUMENTS: -Xcontext-parameters
// "Change parameter 'a' type of function 'foo' to 'Unit'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Unit', but 'Int' was expected.

context(_: String, k: Int)
fun main() {
    foo(<caret>Unit, 1)
}

context(_: String, k: Int)
fun foo(a: Int, b: Int) {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix
