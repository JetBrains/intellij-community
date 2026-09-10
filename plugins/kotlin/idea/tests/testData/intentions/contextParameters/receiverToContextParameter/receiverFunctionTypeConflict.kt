// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// SHOULD_FAIL_WITH: Callable reference 'String::fn' cannot be updated automatically because parameters were converted to/from context parameters

fun <caret>String.fn(x: Int): String = x.toString()

fun usage() {
    val ref: String.(Int) -> String = String::fn
}
