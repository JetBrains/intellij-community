// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'mapIndexed{}.sum()'"
// INTENTION_TEXT_2: "Replace with 'asSequence().mapIndexed{}.sum()'"
// AFTER-WARNING: Parameter 'index' is never used
// AFTER-WARNING: Parameter 'item' is never used
fun foo(list: List<Any>): Int {
    var s = 0
    <caret>for ((index, item) in list.withIndex()) {
        s += getShort(index, item)
    }
    return s
}

fun getShort(index: Int, item: Any): Short = TODO()
