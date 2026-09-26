// MODE: property
class Foo<T> {
	inner class Bar<U>
}

val bar/*<# : |[Foo:kotlin.fqn.class]Foo|<|[kotlin.Int:kotlin.fqn.class]Int|>|.|[Foo.Bar:kotlin.fqn.class]Bar|<|[kotlin.String:kotlin.fqn.class]String|> #>*/ = Foo<Int>().Bar<String>()
val barWithExplicitType: Foo<Int>.Bar<String> = Foo<Int>().Bar<String>()