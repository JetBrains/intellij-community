// PROBLEM: none
// WITH_STDLIB
inline fun <reified T> <caret>T.testFun(): String = (T::class.java).name

fun main() {
    5.testFun()
}