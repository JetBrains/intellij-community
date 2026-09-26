fun consume(value: String) {}

fun test(value: Any?) {
    if (value != null) {
        consume(<caret>)
    }
}

// ABSENT: { itemText: "value = value" }
// EXIST: { itemText: "value =", tailText: " String" }
