// SHOULD_FAIL_WITH: Callable reference 'list::fn' cannot pass its receiver value as a context argument
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <T> <caret>List<T>.fn(x: Int): Int = x

fun usage() {
    val list = listOf("a", "b")
    val ref: (Int) -> Int = list::fn
}
