// MODE: receivers_params
// COMPILER_ARGUMENTS: -Xcontext-parameters

val v = myWithContext("foo") { }

fun <C> myWithContext(context: C, block: context(C)() -> Unit) {
    with(context) { block() }
}