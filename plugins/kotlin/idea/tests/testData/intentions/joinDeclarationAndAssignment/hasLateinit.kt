// AFTER-WARNING: The value 'b' assigned to 'var c: String defined in foo' is never used
// AFTER-WARNING: Variable 'c' is assigned but never accessed
fun foo(a: String, b: String) {
    lateinit var c: String<caret>
    c = a
    c = b
}