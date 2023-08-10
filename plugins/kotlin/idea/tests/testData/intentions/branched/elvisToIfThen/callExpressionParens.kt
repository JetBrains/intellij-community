// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: The expression is unused
fun foo(): String? {
    return "foo"
}

fun bar() {
}

fun main(args: Array<String>) {
    (foo()) ?:<caret> bar()
}
