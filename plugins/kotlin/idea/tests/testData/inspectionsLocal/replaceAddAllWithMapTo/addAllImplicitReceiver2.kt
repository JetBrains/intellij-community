// WITH_STDLIB
// FIX: Replace with 'mapTo'

fun List<String>.test(coll1: MutableCollection<String>) {
    coll1.addAll<caret>(map { it })
}