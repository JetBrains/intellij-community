// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 'x' is never used
fun <T> foo(x: T) {}
fun <K> bar(y: K & Any) {
    foo<caret>(y)
}
