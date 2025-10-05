// WITH_STDLIB
// NO_TEMPLATE_TESTING
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 't' is never used
fun main(args: Array<String>) {
    val t = doSomething("one" + 1,
            "two",
            3 * 4)<caret>!!
}

fun doSomething(vararg a: Any): Any? = null
