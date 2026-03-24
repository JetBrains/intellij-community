// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments -XXLanguage:+ExplicitContextArguments
// LANGUAGE_VERSION: 2.3
class Context
class Context2

context(c1: Context, c2: Context2)
fun fooCtx(v: Int) {}

context(ctx: Context, ctx2: Context2)
fun example() {
    fooCtx(v = 1, c1 = ctx)
}