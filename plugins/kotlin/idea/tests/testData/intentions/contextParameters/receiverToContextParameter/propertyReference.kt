// SHOULD_FAIL_WITH: Callable reference 's::prop' cannot pass its receiver value as a context argument
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

val <caret>String.prop: Int
    get() = 1

fun usage() {
    val s = "str"
    val ref: () -> Int = s::prop
}
