// SHOULD_FAIL_WITH: Callable reference 'String::fn' cannot pass its receiver value as a context argument, Callable reference 's::fn' cannot pass its receiver value as a context argument
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

fun <caret>String.fn(x: Int): String = x.toString()

fun usage() {
    val s = "str"
    val bound: (Int) -> String = s::fn
    val unbound: (String, Int) -> String = String::fn
}
