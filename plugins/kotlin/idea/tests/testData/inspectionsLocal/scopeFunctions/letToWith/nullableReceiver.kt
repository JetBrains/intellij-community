// WITH_STDLIB
// PROBLEM: none

fun test() {
    val text: String? = "  Kotlin  "

    val length = text?.<caret>let { t ->
        val trimmed = t.trim()
    }
}