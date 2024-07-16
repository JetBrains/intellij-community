// WITH_STDLIB


fun foo() {
    "".let<caret> { it + 1 }
}