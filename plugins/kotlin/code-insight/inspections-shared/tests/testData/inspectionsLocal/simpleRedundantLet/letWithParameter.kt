// WITH_STDLIB


fun foo() {
    val foo: String? = null
    foo?.let<caret> {
        text ->
        text.length
    }
}