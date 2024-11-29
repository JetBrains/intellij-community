// PRIORITY: LOW
// AFTER-WARNING: The value 'l[0]' assigned to 'val x: T defined in foo' is never used
// AFTER-WARNING: Variable 'x' is assigned but never accessed
fun <T> foo(l: List<T>) {
    val x =<caret> l[0]
}