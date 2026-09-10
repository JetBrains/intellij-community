// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(x: Int): String = x.toString()

fun String.usage() {
    val ref: (Int) -> String = this@usage::fn
}
