// MODE: all
private val list/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[list.<no name provided>:kotlin.fqn.class]`<no name provided>`|> #>*/ = listOf(object : Iterable<Int> {
        override fun iterator()/*<# : |[kotlin.collections.Iterator:kotlin.fqn.class]Iterator|<|[kotlin.Int:kotlin.fqn.class]Int|> #>*/ = object : Iterator<Int> {
        override fun next()/*<# : |[kotlin.Int:kotlin.fqn.class]Int #>*/ = 1
        override fun hasNext()/*<# : |[kotlin.Boolean:kotlin.fqn.class]Boolean #>*/ = true
    })
}