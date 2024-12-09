// PROBLEM: none
fun foo() {
    val a = when ("") {
        <caret>else -> 1
    }
}