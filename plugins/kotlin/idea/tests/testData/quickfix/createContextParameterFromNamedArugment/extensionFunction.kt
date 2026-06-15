// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// K2_ERROR: No parameter with name 'ctx' found.
// K2_AFTER_ERROR: No context argument for 'ctx: Ctx' found.
// K2_AFTER_ERROR: No parameter with name 'ctx' found.

class Ctx

fun String.f1() {}

fun fdemo() {
    "hello".f1(ctx<caret> = Ctx())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction