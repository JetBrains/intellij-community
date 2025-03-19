// IGNORE_K1
// IGNORE_K2

sealed interface Foo {

    val foo: Int

    operator fun component1(): Int = foo

    val bar: String

    operator fun component2(): String = bar

    val baz: Double

    operator fun component3(): Double = baz
}

data class Bar(
    override val foo: Int = 42,
    override val bar: String = "",
) : Foo

fun bar() {
    Bar().let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// EXIST: { lookupString: "foo", itemText: "(foo, bar, i)", tailText: " -> ", allLookupStrings: "bar, i, foo", typeText: "(Int, String, Double)" }