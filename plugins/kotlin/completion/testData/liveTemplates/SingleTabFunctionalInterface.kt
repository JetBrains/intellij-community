fun interface Foo {
    fun foo(first: String, second: Int)
}

fun test(): Foo {
    return F<caret>
}

// ELEMENT: Foo
// TAIL_TEXT: " { first, second -> ... } (function: (String, Int) -> Unit) (<root>)"
// TABS: 1
