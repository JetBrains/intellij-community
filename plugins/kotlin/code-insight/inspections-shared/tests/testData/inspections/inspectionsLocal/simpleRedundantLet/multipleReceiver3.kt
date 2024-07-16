// WITH_STDLIB
// PROBLEM: none

fun baz(foo: String) {
    foo.let<caret> { it.substring(0, it.length) }
}
