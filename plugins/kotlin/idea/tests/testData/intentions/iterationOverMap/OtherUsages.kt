// IS_APPLICABLE: false
// WITH_STDLIB

fun main(args: Array<String>) {
    val map = hashMapOf(1 to 1)
    for (<caret>entry in map) {
        val myKey = entry.key
        val myValue = entry.value

        println(entry)

    }
}