fun foo(list: MutableList<String>) {
    list.<caret>
}

// IGNORE_K2
// EXIST: { itemText: "add", tailText: "(element: String)", typeText: "Boolean" }
// EXIST: { itemText: "iterator", tailText: "()", typeText: "Iterator<String>" }
