// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// K2_ERROR: No context argument for 'a: A' found.
// K2_ERROR: No context argument for 'b: B' found.
// K2_ERROR: No parameter with name 'ctx2' found.
// K2_AFTER_ERROR: No context argument for 'a: A' found.
// K2_AFTER_ERROR: No context argument for 'b: B' found.
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