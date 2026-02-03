// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 's' is never used
fun f(s: Int?) {
}

fun main(args: Array<String>) {
    val x: String? = "foo"
    f(x?.<caret>length)
}
