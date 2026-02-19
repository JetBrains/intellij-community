// WITH_STDLIB
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xwhen-guards

private fun test(s: Any) {
    when<caret> (s) {
        is String -> println("1")
        is Int if s > 5 -> println("2")
        else if s.toString() == "foo" -> println("3")
        is Int if s < 0 -> println("4")
        else if s.toString() == "bar" -> println("5")
        else -> println("6")
    }
}
