// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <T> <caret>List<T>.fn(x: Int): Int = x

fun usage() {
    val list = listOf("a", "b")
    val ref: (Int) -> Int = list::fn
}
