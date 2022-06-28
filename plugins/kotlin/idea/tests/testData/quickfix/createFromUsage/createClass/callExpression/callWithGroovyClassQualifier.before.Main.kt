// "Create class 'Foo'" "false"
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: Foo

fun test() = J.<caret>Foo(2, "2")