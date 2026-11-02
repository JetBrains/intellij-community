// FIX: Convert to collection builder

fun test(any: List<Any>, m: Map<String, Int>): List<Any> = any +<caret> m.filter { it.value > 0 } + 3