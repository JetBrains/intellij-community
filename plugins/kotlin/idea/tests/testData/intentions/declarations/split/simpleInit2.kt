// AFTER-WARNING: Parameter 'n' is never used
// AFTER-WARNING: The value '""' assigned to 'var x: String defined in foo' is never used
// AFTER-WARNING: Variable 'x' is assigned but never accessed
fun foo(n: Int) {
    <caret>var x = ""
}