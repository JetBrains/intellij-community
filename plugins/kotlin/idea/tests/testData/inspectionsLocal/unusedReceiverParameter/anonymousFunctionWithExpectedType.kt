// PROBLEM: none
// WITH_STDLIB


fun stateKeeper(block: String.() -> Unit) {}

fun test() {
    stateKeeper(fun St<caret>ring.() {})
}
// IGNORE_K1