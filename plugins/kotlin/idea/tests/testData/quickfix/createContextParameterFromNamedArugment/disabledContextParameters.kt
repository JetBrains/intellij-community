// "Add context parameter to function" "false"
// COMPILER_ARGUMENTS: -XXLanguage:-ContextParameters
// K2_ERROR: No parameter with name 'ctx2' found.
// K2_AFTER_ERROR: No parameter with name 'ctx2' found.
class Ctx

fun f1() {
}

fun fdemo() {
    f1(ctx2<caret> = Ctx())
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction