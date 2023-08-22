// PROBLEM: none
// WITH_STDLIB

fun bar(f: () -> Unit) {}
fun bar(f: (Int) -> Unit) {}

fun test() {
    bar({ -><caret> })
}