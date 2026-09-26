// WITH_STDLIB


fun foo() {
    "".let<caret> { it.length + 1 }
}