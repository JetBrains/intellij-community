// MODE: local_variable
fun bar() = { a: String, b: Int -> a.length + b }

fun foo() {
    val a/*<# : |(|[kotlin.String:kotlin.fqn.class]String|, |[kotlin.Int:kotlin.fqn.class]Int|) -> |[kotlin.Int:kotlin.fqn.class]Int #>*/ = bar()
}