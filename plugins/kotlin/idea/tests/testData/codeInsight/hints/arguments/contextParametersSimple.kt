// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context

context(c: Context)
fun fooCtx() {}

context(ctx: Context)
fun example() {
    fooCtx(/*<# [contextParametersSimple.kt:92]c| = |[contextParametersSimple.kt:129]ctx| «  #>*/)
}