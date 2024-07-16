fun main() {
    Foo().bar/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .foo/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar.bar/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar
}