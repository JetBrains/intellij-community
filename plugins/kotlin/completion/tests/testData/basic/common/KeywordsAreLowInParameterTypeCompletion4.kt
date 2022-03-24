class SomeType<T>

val test: ((a: SomeType<<caret>>) -> Unit)? = null

// WITH_ORDER
// EXIST: { itemText: "SomeType" }
// EXIST: { itemText: "in" }
// EXIST: { itemText: "out" }
// EXIST: { itemText: "suspend" }