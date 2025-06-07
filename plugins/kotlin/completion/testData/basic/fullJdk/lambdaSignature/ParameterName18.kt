// IGNORE_K1
// IGNORE_K2

interface Foo

operator fun Foo.component1(): Int = 42

fun bar() {
    object Foo {}.let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// EXIST: { lookupString: "i", itemText: "i", tailText: " -> ", allLookupStrings: "i", typeText: "Int" }