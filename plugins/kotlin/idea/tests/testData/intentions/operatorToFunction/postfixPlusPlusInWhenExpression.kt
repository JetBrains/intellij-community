// AFTER-WARNING: The value 'a.inc()' assigned to 'var a: Int defined in foo' is never used
// AFTER-WARNING: The value changed at 'a--' is never used
fun foo() {
    var a = 0
    when {
        true -> a++<caret>
        else -> a--
    }
}
