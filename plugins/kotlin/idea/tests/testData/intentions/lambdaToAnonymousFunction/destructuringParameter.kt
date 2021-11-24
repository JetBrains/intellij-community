// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(f: (Pair<Int, Int>) -> String) {}

fun test() {
    foo <caret>{ (i, j) -> "" }
}