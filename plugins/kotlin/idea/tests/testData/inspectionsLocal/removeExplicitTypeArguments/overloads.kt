// PROBLEM: none

fun F1(a: Int, b: Int) {}

fun F1(a: Any?, b: Any?) {}

fun <T> F2(): T? {
    return null
}

fun F3() {
    F1(42, F2<Int><caret>())
}