// SHOULD_FAIL_WITH: Callable reference 'get()::fn' cannot pass its receiver value as a context argument
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(x: Int): String = x.toString()

fun get(): String = "str"

fun usage() {
    val ref: (Int) -> String = get()::fn
}
