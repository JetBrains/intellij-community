private fun test(s: Int) {
    when (s) {
        1 -> println("1")
        2    if    s != 3 && s != 4 -> println("2")
        else -> println("3")
    }
}