// IGNORE_K1

object Foo {

    fun component1(): Int = 42

    operator fun component2(): String = ""
}

fun bar() {
    Foo.let { foo<caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// NOTHING_ELSE