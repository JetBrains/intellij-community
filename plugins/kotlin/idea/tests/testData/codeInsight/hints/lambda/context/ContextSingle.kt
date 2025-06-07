// MODE: receivers_params
// COMPILER_ARGUMENTS: -Xcontext-parameters

val v = myWithContext("foo") {/*<# context(|[kotlin.String:kotlin.fqn.class]String|) #>*/
}

fun <C> myWithContext(context: C, block: context(C)() -> Unit) {
    with(context) { block() }
}