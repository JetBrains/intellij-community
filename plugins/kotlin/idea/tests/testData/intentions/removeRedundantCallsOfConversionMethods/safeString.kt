// WITH_STDLIB
// AFTER-WARNING: The expression is unused

fun test() {
    val foo: String? = null
    foo?.toString()<caret>
}