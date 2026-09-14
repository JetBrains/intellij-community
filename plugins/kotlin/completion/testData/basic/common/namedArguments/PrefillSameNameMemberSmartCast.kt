// COMPLETION_TYPE: SMART

class Box(val value: Any)

fun consume(value: String) {}

fun Box.test() {
    if (value is String) {
        consume(<caret>)
    }
}

// EXIST: { itemText: "value = value" }
