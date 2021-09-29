// AFTER-WARNING: Parameter 'args' is never used
fun main(args: Array<String>) {
    val foo: String? = "foo"
    foo?.<caret>length
}
