// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 'x' is never used
inline fun <reified T> foo(x: T) {}
inline fun <reified K> bar(y: K) {
    foo<caret>(y)
}
