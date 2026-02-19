fun main() {
    Foo().nullBar()/*<# [Bar:kotlin.fqn.class]Bar|? #>*/
        ?.bar!!/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .nullBar()/*<# [Bar:kotlin.fqn.class]Bar|? #>*/
        ?.foo()
}