// MODE: local_variable
fun foo(v: () -> String) {
    val bar/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|(|) -> |[kotlin.String:kotlin.fqn.class]String|> #>*/ = listOf(1, 2).mapNotNull{ v }
}
