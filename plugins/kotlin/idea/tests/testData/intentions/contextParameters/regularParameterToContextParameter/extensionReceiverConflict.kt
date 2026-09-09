// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference 'Rec::fn' cannot be updated automatically because parameters were converted to/from context parameters
class Ctx
class Rec

fun Rec.fn(<caret>ctx: Ctx, x: Int): String = x.toString()

fun usage() {
    val ref: (Rec, Ctx, Int) -> String = Rec::fn
}
