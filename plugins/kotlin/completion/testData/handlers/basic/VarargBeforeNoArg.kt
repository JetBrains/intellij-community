// WITH_STDLIB
// FIR_COMPARISON
val list = mutableLi<caret>

// WITH_ORDER
// EXIST: { itemText: "mutableListOf", tailText:"(vararg elements: T) (kotlin.collections)" }
// EXIST: { itemText: "mutableListOf", tailText:"()" }

// ELEMENT: mutableListOf
// TAIL_TEXT: "(vararg elements: T) (kotlin.collections)"