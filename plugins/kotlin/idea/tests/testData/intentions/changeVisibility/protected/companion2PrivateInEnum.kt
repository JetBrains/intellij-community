// IS_APPLICABLE: false
enum class A {
    X, Y, Z;

    companion object {
        @JvmStatic
        <caret>private fun b() {
        }
    }
}
