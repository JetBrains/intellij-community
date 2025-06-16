fun some(list: List<String>) {
    list.<caret>
}

// WITH_ORDER
// EXIST: { lookupString: "[]", itemText: "[]", tailText: "(index: Int)", typeText: "String", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: get