// PROBLEM: none
data class My(val x: Int, val y: String) {
    <caret>override fun equals(other: Any?): Boolean = super.equals(other)
}