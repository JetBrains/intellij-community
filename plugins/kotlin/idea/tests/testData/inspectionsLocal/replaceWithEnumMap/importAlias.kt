// FIX: Replace with 'EnumMap'
// RUNTIME_WITH_FULL_JDK

import kotlin.collections.hashMapOf as foo

enum class E {
    A, B
}

fun getMap(): Map<E, String> = f<caret>oo()