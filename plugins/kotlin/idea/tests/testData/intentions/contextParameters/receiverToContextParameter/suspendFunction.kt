// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

suspend fun <caret>String.fn(x: Int): String = x.toString()

fun usage() {
    val s = "str"
    val ref: suspend (Int) -> String = s::fn
}
