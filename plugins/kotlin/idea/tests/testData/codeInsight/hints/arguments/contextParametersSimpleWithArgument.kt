// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context

context(c: Context)
fun fooCtx(v: Int) {}

context(ctx: Context)
fun example() {
    fooCtx(<hint text="v:"/>1)
}