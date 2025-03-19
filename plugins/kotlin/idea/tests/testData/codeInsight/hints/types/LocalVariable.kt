// MODE: local_variable
fun foo() { val a/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[kotlin.String:kotlin.fqn.class]String|> #>*/ = listOf("a") }
fun <T> doSmth(list: java.util.ArrayList<T>) {
    val t/*<# : |[doSmth.T:kotlin.fqn.class]T #>*/ = list[0]
}
fun <T: String> doSmth2(list: java.util.List<T>) {
    val t/*<# : |T! #>*/ = list[0]
}