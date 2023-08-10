// WITH_STDLIB

fun foo(b: Boolean) {
    val t = Integer.<caret>compare(if (b) 1 else 10, 6)
}