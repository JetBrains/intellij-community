// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// K2_ERROR: No parameter with name 'ctx2' found.
// K2_AFTER_ERROR: No context argument for 'ctx2: Ctx2?' found.
// K2_AFTER_ERROR: No parameter with name 'ctx2' found.
class Ctx
class Ctx2

fun f1() {
}

context(c: Ctx)
fun fdemo() {
    f1(ctx2<caret> = null as Ctx2?)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction