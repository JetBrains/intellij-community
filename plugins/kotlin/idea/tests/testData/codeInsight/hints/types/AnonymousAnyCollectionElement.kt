// MODE: all
fun x() {
    val map/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[kotlin.Any:kotlin.fqn.class]Any|> #>*/ = listOf(1, 2, 3).map {
        object {
            override fun toString(): String = it.toString()
        }
    }
}