// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB
fun test() {
    <caret>if (true) {
        1
    } else {
        2
    }.let { print(it) }
}