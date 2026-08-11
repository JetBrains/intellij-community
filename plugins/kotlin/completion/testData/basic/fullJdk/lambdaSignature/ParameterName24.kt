class Foo<R> {
    operator fun Foo<R>.component1(): Int = 42

    fun <T : Number> bar() {
        Foo<T>().let { <caret> }
    }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo<T>" }
// ABSENT: { lookupString: "i", itemText: "i", tailText: " -> ", allLookupStrings: "i" }