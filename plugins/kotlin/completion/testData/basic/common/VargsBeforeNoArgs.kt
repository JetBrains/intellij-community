// WITH_STDLIB
val list = mutableLi<caret>

// WITH_ORDER
// EXIST: { itemText: "mutableListOf", tailText:"(vararg elements: T) (kotlin.collections)" }
// EXIST: { itemText: "mutableListOf", tailText:"() (kotlin.collections)" }