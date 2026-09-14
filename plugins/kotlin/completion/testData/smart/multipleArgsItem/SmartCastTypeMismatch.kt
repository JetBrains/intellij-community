fun consume(value: String, count: Int) {}

fun test(value: Any?, count: Int) {
    if (value != null) {
        consume(<caret>)
    }
}

// ABSENT: "value, count"
