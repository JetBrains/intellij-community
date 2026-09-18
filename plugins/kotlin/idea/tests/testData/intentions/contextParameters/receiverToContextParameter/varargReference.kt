// SHOULD_FAIL_WITH: Callable reference 's::fn' cannot pass its receiver value as a context argument
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(vararg xs: Int): String = xs.size.toString()

fun usage() {
    val s = "str"
    val ref: (IntArray) -> String = s::fn
}
