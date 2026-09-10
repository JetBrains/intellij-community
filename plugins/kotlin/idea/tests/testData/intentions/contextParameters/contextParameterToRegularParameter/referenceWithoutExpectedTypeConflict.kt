// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference '::fn' cannot be updated automatically because parameters were converted to/from context parameters

class Ctx

context(<caret>ctx: Ctx)
fun fn(x: Int): String = x.toString()

context(c: Ctx)
fun usage() {
    val ref = ::fn
}
