// WITH_STDLIB
// FIX: Convert to 'run'


fun main() {
    val text: String = "Kotlin"

    val length = text.<caret>let { t ->
        val trimmed = t.trim()
        trimmed.length
    }
}