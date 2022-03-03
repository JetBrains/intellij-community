// WITH_STDLIB
// PROBLEM: none

fun bar(f: () -> Unit) {}
fun bar(f: (Int) -> Unit) {}

fun test() {
    bar { it<caret> -> }
}