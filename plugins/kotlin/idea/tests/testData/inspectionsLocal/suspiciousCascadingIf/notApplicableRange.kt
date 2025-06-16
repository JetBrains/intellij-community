// PROBLEM: none
// IGNORE_K1
fun test() {
    if (true) {
        1
    } <caret>else if (true) {
        2
    } else {
        3
    } + 4
}