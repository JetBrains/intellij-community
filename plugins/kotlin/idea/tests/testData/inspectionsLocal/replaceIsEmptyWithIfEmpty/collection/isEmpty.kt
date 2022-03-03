// WITH_STDLIB
fun test(collection: Collection<Int>): Collection<Int> {
    return if (collection.isEmpty<caret>()) {
        listOf(1)
    } else {
        collection
    }
}