// WITH_STDLIB
// FIX: Convert to 'run'
// IGNORE_K1

fun main() {
    val text: String = "Kotlin"

    val length = text.<caret>let { t ->
        val trimmed = t.trim()
        trimmed.length
    }
}