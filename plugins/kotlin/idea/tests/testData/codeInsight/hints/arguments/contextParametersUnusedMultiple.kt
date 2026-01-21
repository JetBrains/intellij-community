// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context1
class Context2

context(_: Context1, _: Context2)
fun fooCtx() {}

context(ctx: Context2, ctx1: Context1)
fun example() {
    fooCtx()
}