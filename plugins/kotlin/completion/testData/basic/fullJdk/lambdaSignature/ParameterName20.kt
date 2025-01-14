// IGNORE_K1

class Foo {

    val foo: Int
}

operator fun Foo.component1(): Int {
    return foo
}

fun bar() {
    Foo().let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// EXIST: { lookupString: "foo", itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Int" }