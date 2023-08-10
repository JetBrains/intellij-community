// FIX: Replace with 'require()' call
// WITH_STDLIB
fun test(b: Boolean) {
    <caret>if (b) {
        throw IllegalArgumentException("test")
    }
}