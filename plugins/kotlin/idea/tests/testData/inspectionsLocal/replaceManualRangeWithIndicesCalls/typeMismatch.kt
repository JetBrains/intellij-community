// WITH_STDLIB
// FIX: Replace with indices
fun <T> List<List<T>>.test() {
    for (x in 0 <caret>until size) {
        val row = mutableListOf<T>()
        row.add(this[0][x])
    }
}