// LANGUAGE_VERSION: 2.4
interface Baz {
    fun foo(): Foo = Foo()
}

class Outer : Baz {
    class Nested {
        val x = this@Outer.foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
            .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
            .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
            .bar()
    }
}