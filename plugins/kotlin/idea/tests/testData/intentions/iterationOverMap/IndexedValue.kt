// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used

fun main(args: Array<String>) {
    for (<caret>indexedValue in listOf("a", "b").withIndex()) {
        println("${indexedValue.index}: ${indexedValue.value}")
    }
}
