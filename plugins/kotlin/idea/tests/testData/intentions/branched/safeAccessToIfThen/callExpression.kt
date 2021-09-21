// AFTER-WARNING: Parameter 'args' is never used
fun foo(): String? = "foo"
fun main(args: Array<String>) {
    foo()?.<caret>length
}
