// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'z' is never used
fun main(args: Array<String>) {
    val x: String? = null
    val y: String? = "Hello"
    val z = (x ?: y)?.<caret>length
}
