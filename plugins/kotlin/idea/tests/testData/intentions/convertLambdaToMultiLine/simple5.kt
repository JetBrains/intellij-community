// WITH_STDLIB
// AFTER-WARNING: Parameter 's' is never used, could be renamed to _
fun test(list: List<String>) {
    list.forEachIndexed { index, s -> println(index) }<caret>
}