// FIX: Convert to collection builder

class Source {
    fun first(): List<Int> = listOf(1)
    fun second(): List<Int> = listOf(2)
    fun third(): List<Int> = listOf(3)

    fun combined(): List<Int> {
        return first() <caret>+ second().map { it + 1 } + third() + 4
    }
}