// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'args' is never used
fun <T> doSomething(a: T) {}

val a: String? = "A"
fun main(args: Array<String>) {
    doSomething(a?.<caret>length)
}
