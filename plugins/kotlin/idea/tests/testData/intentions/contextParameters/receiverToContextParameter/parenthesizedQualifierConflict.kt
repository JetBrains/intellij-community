// SHOULD_FAIL_WITH: Callable reference '(s)::fn' cannot pass its receiver value as a context argument
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(x: Int): String = x.toString()

fun usage() {
    val s = "str"
    val ref: (Int) -> String = (s)::fn
}
