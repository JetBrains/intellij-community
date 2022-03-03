// WITH_STDLIB
// AFTER-WARNING: Variable 'foo' is never used
fun test(i: UByte) {
    val foo = i.toUByte()<caret>
}