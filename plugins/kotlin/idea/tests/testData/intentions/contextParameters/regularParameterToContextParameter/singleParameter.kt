// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: AMBIGUOUS_CONTEXT_ARGUMENT
// K2_AFTER_ERROR: AMBIGUOUS_CONTEXT_ARGUMENT

fun foo(<caret>p: String) {
    p.substring(1)
    println(p == p)
}

context(c: String)
fun String.bar() {
    foo(c)
    foo("baz")
    foo(this)
    "boo".run {
        foo(this)
    }
}

fun test(string: String) = with(string) {
    foo(string)
}

fun testContext(string: String) = context(string) {
    foo(string)
}
