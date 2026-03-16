// WITH_COROUTINES
// PROBLEM: none

fun delay(timeMillis: Long) {}
fun test() {
    del<caret>ay(1000)
}