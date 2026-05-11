fun foo(a: (String, Int, Int) -> Unit) {}

fun test() {
    foo<caret>
}

// ELEMENT: foo
// TAIL_TEXT: " { a: (String, Int, Int) -> Unit } (<root>)"
// TABS: 3
