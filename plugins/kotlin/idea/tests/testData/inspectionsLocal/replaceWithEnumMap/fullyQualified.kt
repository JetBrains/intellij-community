// FIX: Replace with 'EnumMap'
enum class E {
    A, B
}

fun getMap(): Map<E, String> = java.util.Hash<caret>Map()

// IGNORE_K1