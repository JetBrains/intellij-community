// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'y' is never used
fun doSth(): Int? {
    val x: String? = "abc"
    return x?.<caret>length
}
fun main(args: Array<String>) {
    val y = doSth()
}
