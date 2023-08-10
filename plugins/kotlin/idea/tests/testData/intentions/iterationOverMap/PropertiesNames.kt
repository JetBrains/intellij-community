// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'myKey' is never used
// AFTER-WARNING: Variable 'myValue' is never used

fun main(args: Array<String>) {
    val map = hashMapOf(1 to 1)
    for (<caret>entry in map) {
        val myKey = entry.key
        val myValue = entry.value

    }
}