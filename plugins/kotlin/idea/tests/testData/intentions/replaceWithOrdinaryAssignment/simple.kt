// AFTER-WARNING: The value 'x + 1' assigned to 'var x: Int defined in foo' is never used
fun foo() {
    var x = 0
    x <caret>+= 1
}