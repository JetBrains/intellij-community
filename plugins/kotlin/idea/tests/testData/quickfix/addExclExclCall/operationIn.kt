// "Add non-null asserted (!!) call" "true"
// WITH_STDLIB

fun foo(a: List<String>?) {
    "x" <caret>in a
}