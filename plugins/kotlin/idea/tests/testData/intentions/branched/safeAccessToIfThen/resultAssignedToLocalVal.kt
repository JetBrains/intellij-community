// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'y' is never used
fun main(args: Array<String>) {
    val x: String? = "abc"
    val y = x?.<caret>length
}
