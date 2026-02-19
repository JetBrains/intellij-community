inline fun <reified T> <caret>T.testFun(t: T): String = t.toString()

fun main() {
    5.testFun(5)
}
