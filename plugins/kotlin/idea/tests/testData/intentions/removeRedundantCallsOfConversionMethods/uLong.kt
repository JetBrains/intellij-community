// WITH_STDLIB
// AFTER-WARNING: Variable 'foo' is never used
fun test(i: ULong) {
    val foo = i.toULong()<caret>
}