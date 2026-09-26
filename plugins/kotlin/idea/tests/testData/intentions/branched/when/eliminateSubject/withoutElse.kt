// PRIORITY: LOW
// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
enum class Type {
    HYDRO,
    PYRO
}

fun select(t: Type) {
    <caret>when (t) {
        Type.HYDRO -> 1
        Type.PYRO -> 42
    }
}