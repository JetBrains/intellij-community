fun some(list: List<String>) {
    list.apply {
        <caret>
    }
}

// ABSENT: "[]"
