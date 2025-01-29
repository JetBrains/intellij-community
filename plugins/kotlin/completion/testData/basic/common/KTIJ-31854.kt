// IGNORE_K2

fun main() {
    emptyList<String>().map(String::<caret>)
}

// INVOCATION_COUNT: 0
// EXIST: toBoolean, toByte, toShort, toInt, toLong, toFloat, toFloatOrNull, toDouble, toDoubleOrNull