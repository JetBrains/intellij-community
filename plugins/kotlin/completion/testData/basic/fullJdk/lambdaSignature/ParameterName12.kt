// IGNORE_K1

object Foo {

    private operator fun component1(): Int = 42

    operator fun component2(): String = ""
}

fun bar() {
    Foo.let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo", tailText: " -> ", allLookupStrings: "foo", typeText: "Foo" }
// ABSENT: { tailText: " -> ", typeText: "(Int, String)" }