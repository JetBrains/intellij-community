// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: The expression is unused
val a: String? = "A"
fun main(args: Array<String>) {
    a ?:<caret> "bar"
}
