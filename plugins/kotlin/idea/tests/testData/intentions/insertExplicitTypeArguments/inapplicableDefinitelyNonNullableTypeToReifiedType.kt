// IS_APPLICABLE: false

inline fun <reified T> foo(x: T) {}
inline fun <reified K> bar(y: K & Any) {
    foo<caret>(y)
}
