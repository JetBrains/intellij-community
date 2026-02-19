// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context1
class Context2

context(c1: Context1, c2: Context2)
fun fooCtx() {}

context(ctx: Context2, ctx1: Context1)
fun example() {
    fooCtx(/*<# [contextParametersMultiple.kt:108]c1| = |[contextParametersMultiple.kt:176]ctx1| , |[contextParametersMultiple.kt:122]c2| = |[contextParametersMultiple.kt:161]ctx| «  #>*/)
}