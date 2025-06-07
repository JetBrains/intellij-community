// IGNORE_K1

class Foo(
    val foo: Int = 42,
    val bar: String = "",
)

operator fun Foo.component1(): Int = foo

operator fun Foo.component2(): String = bar

fun bar() {
    Foo().let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// EXIST: { lookupString: "foo", itemText: "(foo, bar)", tailText: " -> ", allLookupStrings: "bar, foo", typeText: "(Int, String)" }