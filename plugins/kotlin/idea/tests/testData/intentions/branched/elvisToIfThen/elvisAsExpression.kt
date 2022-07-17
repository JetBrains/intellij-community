// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'x' is never used
fun main(args: Array<String>) {
    val foo: String? = "foo"
    var bar: String = "bar"
    val x = foo ?:<caret> bar
}
