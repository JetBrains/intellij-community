fun test(i: Int) {
    when (i) {
        <caret>in 1..10 -> println(0)
        in 11..20 -> println(0)
        else -> println(1)
    }
}