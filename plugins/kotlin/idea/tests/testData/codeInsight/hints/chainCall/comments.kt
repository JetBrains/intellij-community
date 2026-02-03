fun main() {
    Foo().bar() // comment
        .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
        .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
        .foo()// comment
        .bar()
        .bar()
}