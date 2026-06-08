// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters

// K2_ERROR: No context argument for 'i: Int' found.
context(i: Int, s: String) fun bar() {}

context(s: String) fun foo() {
    <caret>bar()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix