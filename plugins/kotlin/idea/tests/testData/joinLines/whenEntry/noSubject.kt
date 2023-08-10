fun test(i: Int) {
    when {
        <caret>i == 1 -> println(0)
        i == 2 -> println(0)
        else -> println(1)
    }
}