// WITH_STDLIB
// FIX: Replace with 'mapTo'

fun test(coll1: MutableCollection<String>, coll2: List<String>) {
    coll1.addAll<caret>(coll2.map { it })
}