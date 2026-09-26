fun foo(a: (String, Int) -> Unit) {}

fun test() {
    foo<caret>
}

// ELEMENT: foo
// TAIL_TEXT: " { a: (String, Int) -> Unit } (<root>)"
// TABS: 1
