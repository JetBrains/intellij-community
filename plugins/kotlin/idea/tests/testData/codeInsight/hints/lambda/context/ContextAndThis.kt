// MODE: receivers_params
// COMPILER_ARGUMENTS: -Xcontext-parameters

val v = myWithContext("foo", 1) {/*<# context(|[kotlin.String:kotlin.fqn.class]String|) #>*//*<# this: |[kotlin.Int:kotlin.fqn.class]Int #>*/
}

fun <C, R> myWithContext(c: C, r: R, block: context(C) R.() -> Unit) {
    with(c) { r.block() }
}