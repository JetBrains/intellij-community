// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1
fun foo(s: String) {
    s.substring<caret>(0, 10).substringAfterLast(',')
}