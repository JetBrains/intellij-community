// SHOULD_FAIL_WITH: Callable reference '::fn' cannot pass an explicit argument to context parameter 'ctx'
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

fun fn(<caret>ctx: Ctx, x: Int): String = x.toString()

fun accept(f: (Ctx, Int) -> String) {}

fun usage() {
    accept(::fn)
}
