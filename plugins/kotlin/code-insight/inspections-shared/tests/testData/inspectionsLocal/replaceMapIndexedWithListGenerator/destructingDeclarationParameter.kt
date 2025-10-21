// PROBLEM: none
// WITH_STDLIB
fun test() {
    emptyList<Pair<Int, Int>>().<caret>mapIndexed { index, (l, r) ->
        l + r + index
    }.forEach(::println)
}