// MODE: all
fun x() {
    val map/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[x.<anonymous>.<no name provided>:kotlin.fqn.class]`<no name provided>`|> #>*/ = listOf(1, 2, 3).map {
        object {
            override fun toString(): String = it.toString()
        }
    }
}