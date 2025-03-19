// IGNORE_K1

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
    override val baz: Double = 0.0,
) : Foo

fun bar() {
    Bar().let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "bar", tailText: " -> ", allLookupStrings: "bar", typeText: "Bar" }
// EXIST: { itemText: "(foo, bar, baz)", tailText: " -> ", allLookupStrings: "bar, baz, foo", typeText: "(Int, String, Double)" }