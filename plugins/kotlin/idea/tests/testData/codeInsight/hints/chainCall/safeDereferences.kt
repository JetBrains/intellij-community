fun main() {
    Foo()
        .nullBar()/*<# [Bar:kotlin.fqn.class]Bar|? #>*/
        ?.foo!!/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar()
}