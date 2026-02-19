// IGNORE_K1

object Foo {

    fun foo() {}
}

fun bar() {
    foo<caret>
}

// EXIST: { itemText: "Foo.foo", lookupString:"foo", tailText: "()", typeText: "Unit", icon: "Method" }
// INVOCATION_COUNT: 2