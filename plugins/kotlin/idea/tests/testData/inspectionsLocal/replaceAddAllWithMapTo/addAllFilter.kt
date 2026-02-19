// WITH_STDLIB
// FIX: Replace with 'filterTo'

fun test(coll1: MutableCollection<String>, coll2: List<String>) {
    coll1.addAll<caret>(coll2.filter { true })
}