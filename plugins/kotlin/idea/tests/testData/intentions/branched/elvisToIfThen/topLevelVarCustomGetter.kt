// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 'v' is never used
// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
var a: String?
    get() = ""
    set(v) {}

fun main(args: Array<String>) {
    a ?:<caret> "bar"
}
