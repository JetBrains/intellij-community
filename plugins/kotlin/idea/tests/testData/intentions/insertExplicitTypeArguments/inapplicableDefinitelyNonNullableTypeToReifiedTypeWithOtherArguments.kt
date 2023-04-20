// IS_APPLICABLE: false

inline fun <reified T, L> foo(x: T, y: L) {}
inline fun <reified K> bar(y: K & Any) {
    foo<caret>(y, y)
}
