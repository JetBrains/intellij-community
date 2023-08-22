// FIX: Replace with 'requireNotNull()' call
// WITH_STDLIB
fun test(foo: Int?) {
    <caret>if (foo == null) {
        throw IllegalArgumentException("test")
    }
}