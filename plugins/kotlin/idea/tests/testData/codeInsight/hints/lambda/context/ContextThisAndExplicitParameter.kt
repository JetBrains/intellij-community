// MODE: receivers_params
// COMPILER_ARGUMENTS: -Xcontext-parameters

val v = myWithContext("foo", 1, 2.0) {/*<# context(|[kotlin.String:kotlin.fqn.class]String|) #>*/ param ->
}

fun <C, R, I> myWithContext(c: C, r: R, i: I, block: context(C) R.(I) -> Unit) {
    with(c) { r.block(i) }
}