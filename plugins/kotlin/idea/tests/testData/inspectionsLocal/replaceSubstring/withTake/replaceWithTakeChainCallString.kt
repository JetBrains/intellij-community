// WITH_STDLIB


fun foo() {
    "s".substring<caret>(0, 10).
        substringAfterLast(',')
}