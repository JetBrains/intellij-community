// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// K2_ERROR: No parameter with name 'ctx' found.
class Ctx

fun String.f1() {}

fun fdemo() {
    "hello".f1(ctx<caret> = Ctx())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction