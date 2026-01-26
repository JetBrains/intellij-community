// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context

context(c: Context)
fun fooCtx(block: () -> Unit) {}

context(ctx: Context)
fun example() {
    fooCtx /*<# [contextParametersLambda.kt:92]c| = |[contextParametersLambda.kt:146]ctx| «  #>*/{  }
}