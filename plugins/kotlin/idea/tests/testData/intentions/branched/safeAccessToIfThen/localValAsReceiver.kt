// AFTER-WARNING: Parameter 'args' is never used
fun main(args: Array<String>) {
    val x: String? = "abc"
    x?.<caret>length
}
