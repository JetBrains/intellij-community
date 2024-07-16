// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION
// IGNORE_K1
fun String.ext(s: String): String = ""

fun test(a: Any) {
    i<caret>f (a is String) {
        "".ext(a)
    } else null
}