// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// PROBLEM: none
// K2_ERROR: INITIALIZER_REQUIRED_FOR_DESTRUCTURING_DECLARATION
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

data class Foo(val x: String, val y: Int)

fun test(foo: Foo) {
    (<caret>val x, val y = foo
}
