// IS_APPLICABLE: true
// AFTER-WARNING: Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of functional types
// AFTER-WARNING: Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of functional types
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used

inline fun <T, L> foo(x: T, y: L) {}
inline fun <K> bar(y: K & Any) {
    foo<caret>(y, y)
}
