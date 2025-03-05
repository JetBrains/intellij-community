// SHOULD_FAIL_WITH: Function <b><code>genericFunT()</code></b> references type parameters of the containing class
// IGNORE_K2
class Test6<T>(val t: T) {
    fun <caret>genericFunT(): T = t
}