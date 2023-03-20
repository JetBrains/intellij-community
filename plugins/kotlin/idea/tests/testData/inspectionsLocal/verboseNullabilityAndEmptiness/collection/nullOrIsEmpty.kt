// WITH_STDLIB
fun test(collection: Collection<String>?) {
    if (<caret>collection == null || collection.isEmpty()) println(0) else println(collection.size)
}
