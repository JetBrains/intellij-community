// PRIORITY: LOW
// AFTER-WARNING: The value 'x + a / b' assigned to 'var x: Int defined in foo' is never used
fun foo() {
    var x = 0
    val a = 1
    val b = 1
    x <caret>+= a / b
}