// SHOULD_FAIL_WITH: function genericFunT() references type parameters of the containing class
// IGNORE_K1
class Test6<T>(val t: T) {
    fun <caret>genericFunT(): T = t
}