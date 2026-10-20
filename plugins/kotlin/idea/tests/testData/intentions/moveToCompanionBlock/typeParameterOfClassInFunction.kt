// COMPILER_ARGUMENTS: -Xcompanion-blocks
// SHOULD_FAIL_WITH: function emptyOf() references type parameters of the containing class

class A<T>(val value: T) {
    fun <caret>emptyOf(): List<T> = emptyList()
}
