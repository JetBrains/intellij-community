import test.Foo as Zoo

fun foo(): Zoo = Z<caret>

// ELEMENT: Zoo
// TAIL_TEXT: "() (test.Foo)"
// IGNORE_K1