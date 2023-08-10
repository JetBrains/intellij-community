// WITH_STDLIB
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'args' is never used
fun main(args: Array<String>) {
    doSomething("one")<caret>!!
}

fun doSomething(a: Any): Any? = null
