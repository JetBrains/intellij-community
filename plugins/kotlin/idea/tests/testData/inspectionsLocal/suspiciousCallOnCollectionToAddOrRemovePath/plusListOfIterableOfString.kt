// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB

class MyPath(val v: String) : Iterable<String> {
    override fun iterator(): Iterator<String> = v.split("/").iterator()
}

fun test(list: List<MyPath>, path: MyPath) {
    list <caret>+ path
}