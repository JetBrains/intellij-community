// MODE: receivers_params
// COMPILER_ARGUMENTS: -Xcontext-parameters

val v = myWithContext("foo", 1) {/*<# context(|[kotlin.String:kotlin.fqn.class]String|) #>*//*<# it: |[kotlin.Int:kotlin.fqn.class]Int #>*/
}

fun <C, I> myWithContext(c: C, i: I, block: context(C)(I) -> Unit) {
    with(c) { block(i) }
}