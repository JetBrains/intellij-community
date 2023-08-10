// AFTER-WARNING: The value 'a.dec()' assigned to 'var a: Int defined in foo' is never used
fun foo() {
    var a = 5
    a--<caret>
}
