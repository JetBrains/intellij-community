fun foo(x: Int): Int {
    if (x == 1) return 1
    listOf(1, 2, 3).map {
        if (it == 2) return@map 2
        return@<caret>map 3
    }
    return 4
}

//HIGHLIGHTED: return@map 2
//HIGHLIGHTED: map
//HIGHLIGHTED: return@map 3