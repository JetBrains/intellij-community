// WITH_STDLIB
// IGNORE_K1

private fun test(s: Any) {
    when (s) {
        is String,
        is Int <caret>if s > 5 -> { println("1") }
        else -> { println("2") }
    }
}
