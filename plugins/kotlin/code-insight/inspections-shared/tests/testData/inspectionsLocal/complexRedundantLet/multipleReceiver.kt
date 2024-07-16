// WITH_STDLIB
// PROBLEM: none

fun baz(foo: String) {
    foo.let<caret> { it.substringAfterLast(it.capitalize()) }
}
