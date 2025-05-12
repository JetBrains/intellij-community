fun some(list: List<String>) {
    list.<caret>
}

// WITH_ORDER
// EXIST: "[]"
// EXIST: get