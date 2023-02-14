fun foo(a: String, b: String, l: () -> Unit) {

}

fun testIndent() {
    foo("a",
        "b") {
        <caret>
    }
}
