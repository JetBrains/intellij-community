// PROBLEM: none

private fun Any.result(): Int = 0
private fun String.result(): Int = 1

fun test(value: Any): Int =
    if (value is String) <caret>value.result() else value.result()
