// MODE: all
fun m(f: Foo) {
    val entries/*<# : |[kotlin.Array:kotlin.fqn.class]Array|<|(|out|)| |[kotlin.String:kotlin.fqn.class]String|!|> #>*/ = f.getEntries()
}