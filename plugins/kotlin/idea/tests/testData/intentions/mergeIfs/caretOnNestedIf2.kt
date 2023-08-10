// IS_APPLICABLE: false
fun foo() {
    if (true) {
        if (false) {
            foo()
        }<caret>
    }
}