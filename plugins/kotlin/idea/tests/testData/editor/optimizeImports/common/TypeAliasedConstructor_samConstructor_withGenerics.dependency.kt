package dependency

fun interface Foo<T> {
    fun bar(): T
}

typealias TypeAliasedFoo<Param> = Foo<Param>