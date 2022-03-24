// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 't' is never used
fun main(args: Array<String>) {
    val foo: String? = "foo"
    val t = (foo)<caret>!!
}
