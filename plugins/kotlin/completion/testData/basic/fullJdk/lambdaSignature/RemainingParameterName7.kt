// IGNORE_K1

data class Foo(
    val foo: Int,
    val bar: String,
    val baz: Double,
)

fun foo() {
    Foo(42, "", 0.0).let { foo, <caret> }
}

// INVOCATION_COUNT: 0
// NOTHING_ELSE