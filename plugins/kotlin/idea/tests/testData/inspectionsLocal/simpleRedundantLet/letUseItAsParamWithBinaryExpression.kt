// WITH_STDLIB
// PROBLEM: none

fun foo() {
    "".let<caret> { it.length + "".indexOf(it) }
}