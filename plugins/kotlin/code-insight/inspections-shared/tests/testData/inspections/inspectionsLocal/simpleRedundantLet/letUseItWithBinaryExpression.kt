// WITH_STDLIB
// PROBLEM: none

fun foo() {
    "".let<caret> { it.length + it.length }
}