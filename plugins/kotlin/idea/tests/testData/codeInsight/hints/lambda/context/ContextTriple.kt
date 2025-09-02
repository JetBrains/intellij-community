// MODE: receivers_params
// COMPILER_ARGUMENTS: -Xcontext-parameters

val v = myWithContext("foo", 1, 2.0) {/*<# context(|[kotlin.String:kotlin.fqn.class]String|, |[kotlin.Int:kotlin.fqn.class]Int|, |[kotlin.Double:kotlin.fqn.class]Double|) #>*/
}

fun <T1, T2, T3> myWithContext(t1: T1, t2: T2, t3: T3, block: context(T1, T2, T3)() -> Unit) {
    with(t1, t2, t3) { block() }
}