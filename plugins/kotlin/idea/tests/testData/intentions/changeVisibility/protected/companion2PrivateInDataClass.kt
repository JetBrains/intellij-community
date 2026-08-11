// IS_APPLICABLE: false
data class A(val x: Int) {
    companion object {
        @JvmStatic
        <caret>private fun b() {

        }
    }
}
