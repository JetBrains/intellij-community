// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments -XXLanguage:+ExplicitContextArguments
// LANGUAGE_VERSION: 2.3
class Context

context(c: Context)
fun fooCtx(v: Int) {}

context(ctx: Context)
fun example() {
    fooCtx(v = 1, c = ctx)
}