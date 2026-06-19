// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// K2_ERROR: No parameter with name 'ctx' found.
class Ctx

fun <T> f1() {}

fun fdemo() {
    f1<String>(ctx<caret> = Ctx())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction