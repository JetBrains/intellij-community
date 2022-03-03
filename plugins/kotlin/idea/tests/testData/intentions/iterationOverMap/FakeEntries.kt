// WITH_STDLIB
// AFTER-WARNING: Variable 'key' is never used
// AFTER-WARNING: Variable 'value' is never used

class MyMap {
    val entries = listOf<Map.Entry<Int, Int>>()
}

fun foo(mm: MyMap) {
    for (entry<caret> in mm.entries) {
        val (key, value) = entry
    }
}