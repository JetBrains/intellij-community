// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference 'get()::fn' cannot be updated automatically because parameters were converted to/from context parameters

fun <caret>String.fn(x: Int): String = x.toString()

fun get(): String = "str"

fun usage() {
    val ref: (Int) -> String = get()::fn
}
