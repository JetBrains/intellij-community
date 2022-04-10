// PROBLEM: none
// WITH_STDLIB
fun foo(a: Iterator<String>): Sequence<String> {
    return a.asSequ<caret>ence<String>()
}