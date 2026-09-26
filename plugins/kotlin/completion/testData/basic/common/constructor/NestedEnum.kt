interface Foo {
    enum class Bar: Foo {
        BAR
    }
}

fun foo(): Foo {
    return Foo.Bar<caret>
}

// EXIST: {"lookupString": "Bar", "tailText": " (Foo)" }
// ABSENT: {"lookupString": "Bar", "tailText": "() (Foo)" }
// NOTHING_ELSE