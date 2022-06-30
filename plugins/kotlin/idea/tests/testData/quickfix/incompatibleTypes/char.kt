// "Convert string to character literal" "true"
fun test(c: Char) {
    when (c) {
        <caret>"." -> {}
    }
}