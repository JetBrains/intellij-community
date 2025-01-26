// PROBLEM: none

fun <T> test(t: T): T = t

fun usage() {
    test(Runnable<caret> {})
}