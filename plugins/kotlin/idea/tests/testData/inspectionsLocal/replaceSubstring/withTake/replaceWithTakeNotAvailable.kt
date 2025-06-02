// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1
fun foo(s: CharSequence) {
    s.substring<caret>(0, 10).substringAfterLast(',')
}