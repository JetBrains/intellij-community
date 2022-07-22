// PROBLEM: none
fun suspend(body: () -> Int) {}

fun main() {
    val wInvokeCall = suspend()<caret> { 42 }
}