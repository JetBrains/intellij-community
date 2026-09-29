// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// K2_ERROR: UNSUPPORTED_FEATURE

data class Foo(val bar: String, val qux: Int)

fun test() {
    val (<caret>_ = bar, qux) = Foo("", 0)
}
