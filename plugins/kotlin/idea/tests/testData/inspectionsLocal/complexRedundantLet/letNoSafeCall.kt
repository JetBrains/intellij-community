// PROBLEM: none
// WITH_STDLIB


fun foo() {
    val foo: String = ""
    foo.let<caret> {
        it.length
    }
}