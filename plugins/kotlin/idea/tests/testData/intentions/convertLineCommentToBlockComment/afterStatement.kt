fun test() {
    val foo = 1 // comment1<caret>
    // comment2
// AFTER-WARNING: Variable 'foo' is never used
}