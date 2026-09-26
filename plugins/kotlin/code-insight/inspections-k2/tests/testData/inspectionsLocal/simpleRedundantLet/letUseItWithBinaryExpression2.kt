// WITH_STDLIB
// PROBLEM: none

fun foo() {
    "".let<caret> { it.substring(0, 1) + it }
}