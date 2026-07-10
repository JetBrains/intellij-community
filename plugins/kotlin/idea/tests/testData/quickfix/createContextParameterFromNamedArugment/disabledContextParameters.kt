// "Add context parameter to function" "false"
// COMPILER_ARGUMENTS: -XXLanguage:-ContextParameters
// K2_AFTER_ERROR: NAMED_PARAMETER_NOT_FOUND
// K2_ERROR: NAMED_PARAMETER_NOT_FOUND
class Ctx

fun f1() {
}

fun fdemo() {
    f1(ctx2<caret> = Ctx())
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction