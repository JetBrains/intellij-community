// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'args' is never used
fun <T> doSomething(a: T) {}

fun main(args: Array<String>) {
    var a: String? = "A"
    doSomething(a?.<caret>length)
}

