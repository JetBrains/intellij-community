// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context

context(c: Context)
fun fooCtx(v: Int) {}

context(ctx: Context)
fun example() {
    fooCtx(/*<# [contextParametersSimpleWithArgument.kt:92]c| = |[contextParametersSimpleWithArgument.kt:135]ctx| |  #>*//*<# [contextParametersSimpleWithArgument.kt:115]v| = #>*/1)
}