fun some(list: List<String>) {
    list.<caret>
}

// IGNORE_K2
// EXIST: { lookupString: "[]", itemText: "[]", tailText: "(index: Int)", typeText: "String", attributes: "bold", icon: "nodes/abstractMethod.svg"}
