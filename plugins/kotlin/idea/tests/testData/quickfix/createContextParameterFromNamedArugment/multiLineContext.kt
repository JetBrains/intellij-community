// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// K2_AFTER_ERROR: NO_CONTEXT_ARGUMENT
// K2_AFTER_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: NAMED_PARAMETER_NOT_FOUND
// K2_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: NO_CONTEXT_ARGUMENT
class A
class B
class Ctx2

context(
    a: A,
    b: B,
)
fun f1() {
}

fun fdemo() {
    f1(ctx2<caret> = Ctx2())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction