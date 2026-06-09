fun test(block: () -> Unit) {}

fun usage() {
    test(block = {
        TODO()
    <caret>},)
}
