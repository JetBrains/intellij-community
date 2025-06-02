// PROBLEM: none
fun foo() {
    val a<caret> = 1 ?: 2 ?: 3
    when (a) {
        1 -> {
        }
        else -> {
        }
    }
}