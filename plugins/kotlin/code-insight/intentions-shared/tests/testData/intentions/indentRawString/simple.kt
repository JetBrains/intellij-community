// WITH_STDLIB
// AFTER-WARNING: Variable 'foo' is never used
fun test() {
    val foo = <caret>"""foo
bar
baz"""
}