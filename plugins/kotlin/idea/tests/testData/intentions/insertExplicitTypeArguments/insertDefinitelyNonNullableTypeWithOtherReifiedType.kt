// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used

inline fun <reified T, L> foo(x: T, y: L) {}
inline fun <reified K> bar(x: K, y: K & Any) {
    foo<caret>(x, y)
}