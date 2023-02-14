// WITH_STDLIB
// PROBLEM: none

fun baz(foo: String) {
    foo.let<caret> { it.length + "".indexOfLast { c -> c == it[0] } }
}