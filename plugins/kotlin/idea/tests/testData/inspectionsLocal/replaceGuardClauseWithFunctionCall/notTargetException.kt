// PROBLEM: none
// WITH_STDLIB
fun test(b: Boolean) {
    <caret>if (b) throw IndexOutOfBoundsException()
}
