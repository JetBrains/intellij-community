// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -Xexplicit-context-arguments
// K2_ERROR: NAMED_PARAMETER_NOT_FOUND
class Ctx

/** Important function. */
@Suppress("unused")
fun f1() {}

fun fdemo() {
    f1(ctx<caret> = Ctx())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction