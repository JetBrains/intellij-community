class SomeType

fun test(a: <caret>) {}

// WITH_ORDER
// EXIST: { itemText: "SomeType" }
// ABSENT: { itemText: "in" }
// ABSENT: { itemText: "out" }
// EXIST: { itemText: "suspend" }