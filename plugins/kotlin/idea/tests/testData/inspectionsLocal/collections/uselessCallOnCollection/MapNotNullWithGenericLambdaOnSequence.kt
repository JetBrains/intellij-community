// PROBLEM: none
// WITH_STDLIB

fun <R> mylet(block: () -> R): R = block()

val x = sequenceOf(1, 2, 3).<caret>mapNotNull {
    mylet {
        null
    }
}