// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(): Int = 1

fun usage() {
    val s = "str"
    val ref: () -> Int = s::fn
}
