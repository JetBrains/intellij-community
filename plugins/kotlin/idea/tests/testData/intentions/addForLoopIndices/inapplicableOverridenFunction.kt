// WITH_STDLIB
// IS_APPLICABLE: FALSE
fun String.withIndex(): Int = 42

fun foo(s: String) {
    for (<caret>a in s) {

    }
}