// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
open class Ctx
class SubCtx : Ctx()

context(<caret>ctx: Ctx)
fun fn(x: Int): String = x.toString()

context(sub: SubCtx)
fun usage() {
    val ref: (Int) -> String = ::fn
}
