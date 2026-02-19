// SHOULD_FAIL_WITH: property genericValT references type parameters of the containing class
// IGNORE_K1
class Test6<T>(val t: T) {
    val <caret>genericValT: T get() = TODO()
}