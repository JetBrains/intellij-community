// IGNORE_K1

import foo.*

fun bar() {
    Foo().let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// EXIST: { lookupString: "foo", itemText: "(foo, bar)", tailText: " -> ", allLookupStrings: "bar, foo", typeText: "(Int, String)" }