// FIX: Replace with 'check()' call
// WITH_STDLIB
fun test(b: Boolean) {
    <caret>if (b) throw IllegalStateException()
}