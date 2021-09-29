// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'y' is never used
fun main(args: Array<String>) {
    val foo: String? = "foo"
    val y = if (true) foo?.<caret>length else null
}
