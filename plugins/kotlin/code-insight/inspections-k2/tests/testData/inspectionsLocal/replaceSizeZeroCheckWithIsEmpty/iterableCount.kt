// PROBLEM: none
// WITH_STDLIB
// K2_ERROR: FUNCTION_CALL_EXPECTED

fun test(items: Iterable<Int>) {
    ite<caret>ms.count == 0
}