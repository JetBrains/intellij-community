// WITH_STDLIB

fun foo() {
    val t = java.lang.Long.<caret>toString(5, 42) + 6
}
