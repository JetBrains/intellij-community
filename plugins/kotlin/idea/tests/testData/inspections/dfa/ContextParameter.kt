// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// WITH_STDLIB
fun main() {
    with("hello") {
        foo()
    }
}

context(ctx: Any)
fun foo() {
    if (ctx is String) println("$ctx is string") // ← breakpoint
    else {
        if (<warning descr="Condition 'ctx !is String' is always true">ctx !is String</warning>) {
            println("nope")
        }
    }
}