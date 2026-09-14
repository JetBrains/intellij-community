fun consume(value: String) {}

fun test(value: Any) {
    if (value is String) {
        consume(<caret>)
    }
}

// EXIST: { itemText: "value = value" }
// EXIST: { itemText: "value =", tailText: " String" }
