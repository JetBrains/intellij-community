// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
fun foo(): String? {
    return "foo"
}

fun main(args: Array<String>) {
    (foo())<caret>!!
}
