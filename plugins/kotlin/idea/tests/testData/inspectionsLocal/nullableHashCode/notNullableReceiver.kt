// PROBLEM: none

// WITH_STDLIB

fun hash(value: String): Int {
    return value?.hash<caret>Code() ?: 0
}
