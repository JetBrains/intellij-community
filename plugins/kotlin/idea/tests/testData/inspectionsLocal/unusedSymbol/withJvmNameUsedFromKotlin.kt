// WITH_STDLIB
// PROBLEM: none

@JvmName("fooForJava")
fun <caret>foo() {}

fun test() {
    foo()
}