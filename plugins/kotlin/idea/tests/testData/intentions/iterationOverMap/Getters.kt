// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'key' is never used
// AFTER-WARNING: Variable 'value' is never used

fun main(args: Array<String>) {
    val map = hashMapOf(1 to 1)
    for (<caret>entry in map.entries) {
        val key = entry.key
        val value = entry.value

    }
}