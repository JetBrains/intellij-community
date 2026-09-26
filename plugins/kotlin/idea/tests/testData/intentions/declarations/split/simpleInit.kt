// PRIORITY: LOW
// AFTER-WARNING: Parameter 'n' is never used
// AFTER-WARNING: The value '""' assigned to 'val x: String defined in foo' is never used
// AFTER-WARNING: Variable 'x' is assigned but never accessed
fun foo(n: Int) {
    <caret>val x = ""
}