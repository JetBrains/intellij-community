// FIX: Convert to collection builder

fun create(): Set<Int> = setOf(1, 2)

fun main() {
    val a = cr<caret>eate()
}
