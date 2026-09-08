// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

context(<caret>ctx: Ctx)
val prop: Int
    get() = 0

context(c: Ctx)
fun usage() {
    val ref = ::prop
}
