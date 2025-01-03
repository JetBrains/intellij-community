// IGNORE_K1

data class Foo(
    val foo: Int = 42,
    val bar: String = "",
    val baz: Double = 0.0,
)

fun bar() {
    Foo().let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// EXIST: { itemText: "(foo, bar, baz)", tailText: " -> ", allLookupStrings: "bar, baz, foo", typeText: "(Int, String, Double)" }