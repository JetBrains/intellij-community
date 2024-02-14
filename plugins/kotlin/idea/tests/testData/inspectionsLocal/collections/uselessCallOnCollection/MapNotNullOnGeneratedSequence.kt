// WITH_STDLIB

fun fibonacci() = sequence {
    var terms = Pair(0, 1)
    while (true) {
        yield(terms.first)
        terms = Pair(terms.second, terms.first + terms.second)
    }
}

val z = fibonacci().take(10).<caret>mapNotNull { it * it }.sum()