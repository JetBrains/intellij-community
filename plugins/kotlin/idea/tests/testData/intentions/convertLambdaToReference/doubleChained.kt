// WITH_STDLIB
// AFTER-WARNING: Parameter 'data' is never used

interface Data
val data: Data? = null

class Recycler(val adapter: DataAdapter)
val recycler = Recycler(DataAdapter())

class DataAdapter {
    fun newData(data: Data) {}
}

fun test() {
    data?.let { <caret>recycler.adapter.newData(it) }
}