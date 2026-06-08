// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters

// K2_ERROR: No context argument for 'i: Int' found.
// K2_AFTER_ERROR: No context argument for '_: Int' found.
context(i: Int) fun bar() {}

fun outer() {
    fun inner() {
        <caret>bar()
    }
    inner()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix