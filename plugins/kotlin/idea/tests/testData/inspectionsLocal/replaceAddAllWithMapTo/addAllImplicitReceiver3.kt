// WITH_STDLIB
// FIX: Replace with 'mapTo'

fun MutableList<String>.foo() {
    "hello".run {
        addAll<caret>(listOf("").map { it })
    }
}