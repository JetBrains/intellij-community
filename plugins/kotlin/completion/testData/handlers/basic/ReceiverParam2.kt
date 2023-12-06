package foo

fun <R> bar(block: () -> R): R = TODO()
fun <T, R> T.bar(block: T.() -> R): R = TODO()

class X {
    val b = ba<caret>
}

// IGNORE_K2
// TAIL_TEXT: " {...} (block: () -> R) (foo)"