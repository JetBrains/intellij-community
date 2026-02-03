// AFTER-WARNING: The value '2' assigned to 'var s: Int defined in foo' is never used
fun foo() {
    var s<caret>: Int
    s = 1
    s.hashCode()
    s = 2
}