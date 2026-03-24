// PROBLEM: none
class TextField {
    fun isBlank(): Boolean = false
}

fun test2(field: TextField?) {
    if (<caret>field == null || field.isBlank()) {}
}