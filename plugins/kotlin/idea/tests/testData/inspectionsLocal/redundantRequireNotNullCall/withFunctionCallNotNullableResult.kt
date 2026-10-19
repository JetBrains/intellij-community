// WITH_STDLIB

fun fn(f: () -> Int) {
    val x = <caret>requireNotNull(f())
}