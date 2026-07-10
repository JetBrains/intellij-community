// "Add explicit context arguments" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// DISABLE_K2_ERRORS
context(x: String, y: Int)
fun foo2(a: String): String = x + a

fun main() {
    <caret>foo2("Hello", a = "World")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix