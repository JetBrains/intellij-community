fun main() {
    Foo().foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar().foo()
        .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .foo()
}