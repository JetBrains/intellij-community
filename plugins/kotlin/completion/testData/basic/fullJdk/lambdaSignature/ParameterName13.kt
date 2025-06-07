// IGNORE_K1

data object Foo {

    operator fun component1(): Int = 42

    operator fun component2(): String = ""
}

fun bar() {
    Foo.let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// EXIST: { lookupString: "i", itemText: "(i, string)", tailText: " -> ", allLookupStrings: "i, string", typeText: "(Int, String)" }