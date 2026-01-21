// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context1
class Context2

context(c1: Context1, c2: Context2)
fun fooCtx(v: Int) {}

context(ctx: Context2, ctx1: Context1)
fun example() {
    fooCtx(/*<# [contextParametersMultipleWithArgument.kt:108]c1| = |[contextParametersMultipleWithArgument.kt:182]ctx1| » | ,  #>*//*<# [contextParametersMultipleWithArgument.kt:147]v| = #>*/1)
}