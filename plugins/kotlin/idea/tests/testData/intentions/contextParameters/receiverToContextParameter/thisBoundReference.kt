// SHOULD_FAIL_WITH: Callable reference 'this::fn' cannot pass its receiver value as a context argument
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(x: Int): String = x.toString()

fun String.usage() {
    val ref: (Int) -> String = this::fn
}
