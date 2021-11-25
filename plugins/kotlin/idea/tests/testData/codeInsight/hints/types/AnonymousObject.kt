// MODE: function_return
val o = object : Iterable<Int> {
    override fun iterator()<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Iterator.kt:630]Iterator < [jar://kotlin-stdlib-sources.jar!/kotlin/Primitives.kt:21362]Int >] #> = object : Iterator<Int> {
        override fun next()<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Primitives.kt:21362]Int] #> = 1
        override fun hasNext()<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Boolean.kt:618]Boolean] #> = true
    }
}
