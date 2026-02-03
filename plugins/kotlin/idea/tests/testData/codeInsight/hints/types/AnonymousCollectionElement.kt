// MODE: all
interface Some

fun x() {
    val mapNotNull/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[x.<anonymous>.<no name provided>:kotlin.fqn.class]`<no name provided>`|> #>*/ = listOf(1, 2, 3).map {
        object : Some {
            override fun toString(): String = it.toString()
        }
    }
}