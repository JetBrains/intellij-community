// WITH_STDLIB
fun test(collection: Collection<Int>): Collection<Int> {
    return <caret>if (collection.isNotEmpty()) {
        collection
    } else {
        listOf(1)
    }
}