// FIX: Replace with 'require()' call
// WITH_STDLIB
fun test(foo: Boolean) {
    <caret>if (!foo) throw IllegalArgumentException("test")
}