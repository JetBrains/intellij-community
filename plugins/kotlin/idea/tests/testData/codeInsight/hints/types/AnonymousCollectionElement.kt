// MODE: all
interface Some

fun x() {
    val mapNotNull/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[Some:kotlin.fqn.class]Some|> #>*/ = listOf(1, 2, 3).map {
        object : Some {
            override fun toString(): String = it.toString()
        }
    }
}