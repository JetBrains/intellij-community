// PROBLEM: none
// WITH_RUNTIME

fun <R> mylet(block: () -> R): R = block()

val x = listOf(1, 2, 3).<caret>mapNotNull {
    mylet {
        null
    }
}