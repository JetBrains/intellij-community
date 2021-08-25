// PROBLEM: none
fun test(a: Boolean, b: Boolean) {
    when {
        a && b -> {}
        a && !b -> {}
        <caret>!a && b -> {}
        else -> {}
    }
}