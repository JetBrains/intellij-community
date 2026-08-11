// PROBLEM: none
data class My(val x: Int, val y: String) {
    <caret>override fun toString(): String = super.toString()
}