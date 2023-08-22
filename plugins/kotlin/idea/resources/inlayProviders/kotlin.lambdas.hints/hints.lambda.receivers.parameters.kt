fun printString(str: String) {
    str.run {
        println(length)
    }
}

fun <R> run(block: () -> R): R = TODO()