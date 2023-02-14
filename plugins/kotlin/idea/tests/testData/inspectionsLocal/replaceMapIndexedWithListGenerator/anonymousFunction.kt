// WITH_STDLIB
fun test() {
    emptyList<String>().<caret>mapIndexed(fun(index: Int, _: String): Int {
        return index
    })
}