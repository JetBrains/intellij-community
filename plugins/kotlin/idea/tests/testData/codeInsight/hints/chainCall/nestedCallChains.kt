fun main() {
    Foo().foo {
        Bar().foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
            .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
            .foo()
    }/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar()
}