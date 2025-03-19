fun main() {
    Foo().bar()!!/*<# [Bar:kotlin.fqn.class]Bar #>*/
            .foo++/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .foo[1]/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .nullFoo()!!/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .foo()()/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .bar
}