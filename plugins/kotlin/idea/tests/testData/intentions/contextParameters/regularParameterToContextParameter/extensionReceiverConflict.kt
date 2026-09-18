// SHOULD_FAIL_WITH: Callable reference 'Rec::fn' cannot pass an explicit argument to context parameter 'ctx'
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx
class Rec

fun Rec.fn(<caret>ctx: Ctx, x: Int): String = x.toString()

fun usage() {
    val ref: (Rec, Ctx, Int) -> String = Rec::fn
}
