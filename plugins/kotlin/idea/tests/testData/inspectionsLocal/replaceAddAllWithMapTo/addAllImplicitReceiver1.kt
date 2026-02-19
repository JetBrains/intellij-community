// WITH_STDLIB
// FIX: Replace with 'mapTo'

fun MutableCollection<String>.test(coll2: List<String>) {
    addAll<caret>(coll2.map { it })
}