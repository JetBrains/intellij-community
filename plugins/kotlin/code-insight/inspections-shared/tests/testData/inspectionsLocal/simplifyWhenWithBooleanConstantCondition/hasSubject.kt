// PROBLEM: none
// WITH_STDLIB
fun test(b: Boolean) {
    <caret>when(b) {
        true -> println(1)
        false -> println(2)
    }
}