// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(x: Int): String = x.toString()

fun accept(f: (Int) -> String) {}

fun usage() {
    val s = "str"
    accept(s::fn)
}
