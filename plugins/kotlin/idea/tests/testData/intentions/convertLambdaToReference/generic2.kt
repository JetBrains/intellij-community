// WITH_STDLIB
// AFTER-WARNING: Parameter 'i' is never used
fun <T> foo(i: Int): T? = null

fun test(list: List<Int>) {
    list.mapNotNull <caret>{ foo<String>(it) }
}