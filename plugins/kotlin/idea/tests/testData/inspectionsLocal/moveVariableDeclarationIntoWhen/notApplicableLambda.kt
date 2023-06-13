// PROBLEM: none
// WITH_STDLIB
fun foo() {
    val a<caret> = listOf(1).filter { it > 0 }.max()
    when (a) {
        1 -> {
        }
        else -> {
        }
    }
}