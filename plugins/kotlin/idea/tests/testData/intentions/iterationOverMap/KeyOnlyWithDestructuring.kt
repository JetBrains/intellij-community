// WITH_STDLIB
// AFTER-WARNING: Variable 'key' is never used

fun foo(map: Map<Int, Int>) {
    for (entry<caret> in map.entries) {
        val (key) = entry
    }
}