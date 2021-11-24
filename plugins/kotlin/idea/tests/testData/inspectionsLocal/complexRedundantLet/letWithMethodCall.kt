// PROBLEM: none
// WITH_STDLIB


fun foo() {
    val foo: String? = null
    foo?.let<caret> {
        it.to("")
    }
}