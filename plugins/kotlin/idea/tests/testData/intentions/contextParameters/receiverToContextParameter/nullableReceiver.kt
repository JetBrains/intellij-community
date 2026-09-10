// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String?.fn(x: Int): String = x.toString()

fun usage() {
    val s: String? = "str"
    val ref: (Int) -> String = s::fn
}
