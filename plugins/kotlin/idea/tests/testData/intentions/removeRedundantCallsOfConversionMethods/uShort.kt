// WITH_STDLIB
// AFTER-WARNING: Variable 'foo' is never used
fun test(i: UShort) {
    val foo = i.toUShort()<caret>
}