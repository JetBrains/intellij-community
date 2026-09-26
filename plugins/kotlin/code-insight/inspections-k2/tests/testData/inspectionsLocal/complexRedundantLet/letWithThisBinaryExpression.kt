// WITH_STDLIB


fun Int.foo() {
    let<caret> { it.dec() + 1 }
}