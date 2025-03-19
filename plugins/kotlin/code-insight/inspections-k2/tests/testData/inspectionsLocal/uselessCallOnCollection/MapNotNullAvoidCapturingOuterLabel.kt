// PROBLEM: none
// WITH_STDLIB


val a = listOf(1, 2, 3).map {
    // Here we do not want the mapNotNull to change to map, because it would capture the inner label.
    listOf(1, 2, 3).<caret>mapNotNull {
        if (it == 2) return@map 1
        it
    }
}