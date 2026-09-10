// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(vararg xs: Int): String = xs.size.toString()

fun usage() {
    val s = "str"
    val ref: (IntArray) -> String = s::fn
}
