// "Add context parameter to function" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters

// K2_ERROR: No context argument for 'i: Int' found.
// K2_AFTER_ERROR: No context argument for 'i: Int' found.
context(i: Int) fun bar() {}

val action = {
    <caret>bar()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix