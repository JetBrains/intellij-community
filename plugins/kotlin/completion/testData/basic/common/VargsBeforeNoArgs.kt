// WITH_STDLIB
val list = mutableLi<caret>

// IGNORE_K2
// WITH_ORDER
// EXIST: { itemText: "mutableListOf", tailText:"(vararg elements: T) (kotlin.collections)" }
// EXIST: { itemText: "mutableListOf", tailText:"() (kotlin.collections)" }