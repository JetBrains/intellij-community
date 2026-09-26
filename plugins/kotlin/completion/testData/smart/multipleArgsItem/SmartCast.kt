fun consume(value: String, count: Int) {}

fun test(value: Any, count: Int) {
    if (value is String) {
        consume(<caret>)
    }
}

// EXIST: "value, count"
