// WITH_STDLIB
fun test() {
    wh<caret>en {
        true -> {
            println(1)
        }
        else -> {
            println(2)
        }
    }
}