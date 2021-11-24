class SomeType<T>

fun test(a: SomeType<<caret>>) {}

// WITH_ORDER
// EXIST: { itemText: "SomeType" }
// EXIST: { itemText: "in" }
// EXIST: { itemText: "out" }
// EXIST: { itemText: "suspend" }