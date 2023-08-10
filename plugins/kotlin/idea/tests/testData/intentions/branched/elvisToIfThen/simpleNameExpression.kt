// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
fun main(args: Array<String>) {
    val foo: String? = "foo"
    var bar = "bar"
    foo ?:<caret> bar
}
