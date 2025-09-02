// WITH_STDLIB

// IGNORE_K1
fun foo() {
    "s".substring<caret>(0, 10).
        substringAfterLast(',')
}