// AFTER-WARNING: The value 'a.inc()' assigned to 'var a: Int defined in foo' is never used
fun foo() {
    var a = 0
    ++<caret>a
}
