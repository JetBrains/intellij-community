// SHOULD_FAIL_WITH: Callable reference '::fn' cannot be updated to a member with an extension receiver
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

class Foo {
    context(<caret>ctx: Ctx)
    fun fn(x: Int): String = x.toString()

    context(c: Ctx)
    fun usage() {
        val ref: (Int) -> String = ::fn
    }
}
