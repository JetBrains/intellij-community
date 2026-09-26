// MODE: function_return
val o/*<# : |[kotlin.collections.Iterable:kotlin.fqn.class]Iterable|<|[kotlin.Int:kotlin.fqn.class]Int|> #>*/ = object : Iterable<Int> {
        override fun iterator()/*<# : |[kotlin.collections.Iterator:kotlin.fqn.class]Iterator|<|[kotlin.Int:kotlin.fqn.class]Int|> #>*/ = object : Iterator<Int> {
        override fun next()/*<# : |[kotlin.Int:kotlin.fqn.class]Int #>*/ = 1
        override fun hasNext()/*<# : |[kotlin.Boolean:kotlin.fqn.class]Boolean #>*/ = true
    }
}
