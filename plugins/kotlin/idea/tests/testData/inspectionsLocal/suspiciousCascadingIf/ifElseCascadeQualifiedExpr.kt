// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB
fun test() {
    <caret>if (true) {
        1
    } else if (true) 2 else 3.let { print(it) }
}