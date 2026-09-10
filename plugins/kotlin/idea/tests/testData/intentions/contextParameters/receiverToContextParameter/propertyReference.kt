// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

val <caret>String.prop: Int
    get() = 1

fun usage() {
    val s = "str"
    val ref: () -> Int = s::prop
}
