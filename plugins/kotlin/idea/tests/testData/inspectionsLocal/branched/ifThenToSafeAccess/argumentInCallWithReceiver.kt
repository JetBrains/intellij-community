// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION

fun String.ext(s: String): String = ""

fun test(a: Any) {
    i<caret>f (a is String) {
        "".ext(a)
    } else null
}