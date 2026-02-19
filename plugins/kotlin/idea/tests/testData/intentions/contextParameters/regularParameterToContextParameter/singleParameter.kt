// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: Multiple potential context arguments for 'p: String' in scope.
// K2_AFTER_ERROR: Multiple potential context arguments for 'p: String' in scope.

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
