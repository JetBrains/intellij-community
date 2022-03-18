// WITH_STDLIB
// PROBLEM: Replace subsequent checks with 'isNullOrBlank()' call
// FIX: Replace with 'isNullOrBlank()' call
fun test(str: String?) {
    if (<caret>str == null || str.isBlank()) println(0) else println(str.length)
}
