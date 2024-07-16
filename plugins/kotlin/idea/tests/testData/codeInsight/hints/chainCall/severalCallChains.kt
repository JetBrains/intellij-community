fun main() {
    Foo()
        .nullBar()/*<# [Bar:kotlin.fqn.class]Bar|? #>*/
        ?.foo!!/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar()

    Bar().foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar().foo()
        .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .foo()
}
