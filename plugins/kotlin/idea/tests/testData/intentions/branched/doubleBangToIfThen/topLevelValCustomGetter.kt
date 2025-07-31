// WITH_STDLIB
// NO_TEMPLATE_TESTING
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'x' is never used

val a: String?
    get() = ""

fun main(args: Array<String>) {
    val x = a<caret>!!
}
