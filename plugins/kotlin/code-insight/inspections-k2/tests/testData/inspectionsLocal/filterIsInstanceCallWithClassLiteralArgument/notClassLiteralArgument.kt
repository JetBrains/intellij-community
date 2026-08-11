// WITH_STDLIB
// PROBLEM: none

fun foo(list: List<*>, klass: Class<Int>) {
    list.<caret>filterIsInstance(klass)
}