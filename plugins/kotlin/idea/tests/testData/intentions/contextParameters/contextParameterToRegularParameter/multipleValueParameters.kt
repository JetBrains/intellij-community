// SHOULD_FAIL_WITH: Callable reference '::fn' will no longer match the expected function type
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Ctx

context(<caret>ctx: Ctx)
fun fn(a: Int, b: String): String = "$a$b"

context(c: Ctx)
fun usage() {
    val ref: (Int, String) -> String = ::fn
}
