// PROBLEM: none

// WITH_STDLIB

class SymbolTest {
    private operator fun Regex.conta<caret>ins(text: CharSequence): Boolean = true

    fun main() {
        when ("test") {
            in Regex("1[1-6]\\..+") -> true
        }
    }
}