// PROBLEM: none
fun foo() {
    val a<caret> = if (true) 0 else 1
    when (a) {
        1 -> {
        }
        else -> {
        }
    }
}