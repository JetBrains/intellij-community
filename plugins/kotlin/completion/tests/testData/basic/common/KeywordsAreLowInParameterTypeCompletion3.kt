class SomeType

val test: ((a: <caret>) -> Unit)? = null

// WITH_ORDER
// EXIST: { itemText: "SomeType" }
// ABSENT: { itemText: "in" }
// ABSENT: { itemText: "out" }
// EXIST: { itemText: "suspend" }