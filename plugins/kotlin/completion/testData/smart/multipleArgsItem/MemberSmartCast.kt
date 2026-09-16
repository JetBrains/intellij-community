class Box(val value: Any)

fun consume(value: String, count: Int) {}

fun Box.test(count: Int) {
    if (value is String) {
        consume(<caret>)
    }
}

// EXIST: "value, count"
