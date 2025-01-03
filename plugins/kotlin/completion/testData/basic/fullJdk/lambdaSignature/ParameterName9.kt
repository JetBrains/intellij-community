// IGNORE_K1

object Foo {

    operator fun component0(): Int = ""

    operator fun component2(): String = ""
}

fun bar() {
    Foo.let { foo<caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// NOTHING_ELSE