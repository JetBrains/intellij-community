// ERROR: Companion object of enum class 'E' is uninitialized here
// ERROR: Variable 'foo' must be initialized
// AFTER_ERROR: Companion object of enum class 'E' is uninitialized here
enum class E(val value: String) {
    E1(E.<caret>Companion.foo);

    companion object {
        const val foo = ""
    }
}
