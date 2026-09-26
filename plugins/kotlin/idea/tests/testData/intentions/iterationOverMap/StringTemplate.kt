// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used

fun main(args: Array<String>) {
    val map = hashMapOf(1 to "one")
    for (<caret>entry in map) {
        println("${entry.key} => ${entry.value}")
    }
}
