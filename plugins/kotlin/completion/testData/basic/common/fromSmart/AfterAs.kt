fun foo(list: List<String>){}

fun bar(o: Any) {
    foo(o as <caret>)
}

// IGNORE_K2
// EXIST: { itemText: "List<String>" }
