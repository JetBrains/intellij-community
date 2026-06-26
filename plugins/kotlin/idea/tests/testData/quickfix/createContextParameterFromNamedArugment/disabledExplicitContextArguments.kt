// "Add context parameter to function" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:-ExplicitContextArguments
// K2_ERROR: No parameter with name 'ctx2' found.
// K2_AFTER_ERROR: No parameter with name 'ctx2' found.
class Ctx
class Ctx2<T>

context(ctx: Ctx)
fun f1() {
}

context(c: Ctx)
fun fdemo() {
    f1(ctx2<caret> = Ctx2<String>())
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction