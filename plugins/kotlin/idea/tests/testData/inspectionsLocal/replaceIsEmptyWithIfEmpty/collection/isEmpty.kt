// WITH_STDLIB
fun test(collection: Collection<Int>): Collection<Int> {
    return <caret>if (collection.isEmpty()) {
        listOf(1)
    } else {
        collection
    }
}