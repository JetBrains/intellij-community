// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// K2_ERROR: No context argument for 's: String' found.
/**
 *
 */
@Deprecated("use something else")
fun foo() {
    bar<caret>()
}

context(s: String)
fun bar() {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForEnclosingFunction