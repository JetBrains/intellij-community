// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context1
class Context2

context(c1: Context1, c2: Context2)
fun fooCtx(v: Int) {}

context(ctx: Context2, ctx1: Context1)
fun example() {
    fooCtx(<hint text="v:"/>1)
}