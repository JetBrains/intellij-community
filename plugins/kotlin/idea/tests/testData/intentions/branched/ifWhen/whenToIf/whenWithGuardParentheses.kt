// WITH_STDLIB
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xwhen-guards

private fun test(s: Any) {
    when (s) {
        is String -> println("1")
        is Int <caret>if (s > 5) -> { println("2") }
        else -> { println("3") }
    }
}
