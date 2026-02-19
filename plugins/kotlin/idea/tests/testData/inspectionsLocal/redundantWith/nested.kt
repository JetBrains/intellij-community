// WITH_STDLIB
fun test() {
    <caret>with ("") {
        with ("a") {
            this
        }
    }
}