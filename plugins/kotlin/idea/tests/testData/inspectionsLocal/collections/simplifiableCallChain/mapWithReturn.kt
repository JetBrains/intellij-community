// PROBLEM: none
// WITH_STDLIB

fun test(args: List<Int>): String {
    return args.<caret>map {
        if (it == 0) return ""
        "$it * $it"
    }.joinToString(separator = " + ")
}