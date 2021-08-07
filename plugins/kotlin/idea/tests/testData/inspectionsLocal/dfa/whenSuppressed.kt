// PROBLEM: none
fun test(a: Boolean, b: Boolean, c: Boolean) {
    when {
        a && b && c -> {}
        a && b && !c -> {}
        a && !b && c -> {}
        a && <caret>!b && !c -> {}
    }
}