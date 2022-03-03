fun foo(x: Int): Int {
    if (x == 1) return 1
    listOf(1, 2, 3).map {
        if (it == 2) return@map 2
        return@map 3
    }
    <caret>return 4
}

//HIGHLIGHTED: return 1
//HIGHLIGHTED: foo
//HIGHLIGHTED: return 4