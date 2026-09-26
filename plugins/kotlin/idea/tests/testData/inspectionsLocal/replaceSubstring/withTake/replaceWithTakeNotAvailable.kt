// WITH_STDLIB
// PROBLEM: none

fun foo(s: CharSequence) {
    s.substring<caret>(0, 10).substringAfterLast(',')
}