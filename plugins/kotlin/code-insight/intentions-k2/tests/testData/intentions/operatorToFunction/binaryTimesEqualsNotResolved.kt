// AFTER-WARNING: The value 'a.times(b)' assigned to 'var a: Int defined in foo' is never used
fun foo(b: Int) {
    var a = 0
    a <caret>*= b
}
