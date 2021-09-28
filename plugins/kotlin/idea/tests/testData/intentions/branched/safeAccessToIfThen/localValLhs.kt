// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'args' is never used
fun <T> doSomething(a: T) {}

fun main(args: Array<String>) {
    val a: String? = "A"
    doSomething(a?.<caret>length)
}
