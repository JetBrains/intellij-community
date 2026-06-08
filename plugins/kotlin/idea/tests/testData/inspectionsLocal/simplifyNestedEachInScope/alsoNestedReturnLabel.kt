// WITH_STDLIB
fun test() {
    listOf(1, 2, 3).also<caret> {
        it.forEach {
            val x = run label@{
                return@label if (true) {
                    return@forEach
                    0
                }
                else {
                    0
                }
            }
            println(x)
        }
    }
}

