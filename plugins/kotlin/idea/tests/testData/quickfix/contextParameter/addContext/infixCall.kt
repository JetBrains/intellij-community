// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters

// K2_ERROR: No context argument for 'i: Int' found.
context(i: Int) infix fun String.with(n: Int): String = ""

fun foo() {
    val r = "hello" <caret>with 3
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix