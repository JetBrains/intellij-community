// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <T> <caret>T.fn(x: Int): String = x.toString()

fun usage() {
    val s = "str"
    val ref: (Int) -> String = s::fn
}
