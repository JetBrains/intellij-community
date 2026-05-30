// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
fun <T> foo(t1: T, t2: T) = Unit

fun test() {
    foo(list<caret>Of<String>(), listOf())
}