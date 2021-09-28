// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: The expression is unused
fun bar(): String = "bar"

fun main(args: Array<String>) {
    val foo: String? = "foo"
    (foo) ?:<caret> bar()
}
