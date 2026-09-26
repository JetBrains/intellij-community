// WITH_STDLIB
// AFTER-WARNING: Variable 'key' is never used
// AFTER-WARNING: Variable 'value' is never used

fun foo(map: Map<Int, Int>) {
    for (entry<caret> in map.entries) {
        val (key, value) = entry
    }
}