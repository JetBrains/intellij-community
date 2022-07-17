// PROBLEM: none
// WITH_STDLIB

fun foo(s: String) {
    s.substring<caret>(3, s.length - 5)
}