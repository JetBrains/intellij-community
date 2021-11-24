// FIX: Replace with 'require()' call
// WITH_STDLIB
fun test(foo: Int) {
    <caret>if (foo != 0) throw IllegalArgumentException("test")
}