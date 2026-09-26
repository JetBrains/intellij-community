// FIX: Replace with 'checkNotNull()' call
// WITH_STDLIB
fun test(foo: Int?) {
    <caret>if (foo == null) throw IllegalStateException()
}