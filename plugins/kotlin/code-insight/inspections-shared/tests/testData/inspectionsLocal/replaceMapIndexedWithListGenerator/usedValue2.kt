// PROBLEM: none
// WITH_STDLIB
fun test() {
    emptyList<String>().<caret>mapIndexed(fun(index: Int, value: String) {
        println(value)
        index
    })
}