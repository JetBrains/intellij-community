// WITH_STDLIB
fun String.foo() {}

fun test(s: String?) {
    s?.let<caret> { it.foo() }
}