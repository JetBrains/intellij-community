// WITH_STDLIB
// FIX: Replace with 'filterTo'

fun test(coll1: MutableCollection<String>, coll2: List<String>) {
    coll1.plusAssign<caret>(coll2.filter { true })
}