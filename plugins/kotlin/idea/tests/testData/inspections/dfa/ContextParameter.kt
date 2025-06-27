// WITH_STDLIB
fun main() {
    with("hello") {
        <error descr="[UNSUPPORTED_CONTEXTUAL_DECLARATION_CALL] To call contextual declarations, specify the '-Xcontext-parameters' compiler option.">foo</error>()
    }
}

<error descr="[UNSUPPORTED_FEATURE] The feature \"context parameters\" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xcontext-parameters', but note that no stability guarantees are provided.">context(ctx: Any)</error>
fun foo() {
    if (ctx is String) println("$ctx is string") // ← breakpoint
    else {
        if (<warning descr="Condition 'ctx !is String' is always true">ctx !is String</warning>) {
            println("nope")
        }
    }
}