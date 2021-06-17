// PROBLEM: none
// WITH_RUNTIME
fun test(obj : Any) {
    when(obj) {
        <caret>0 -> {}
        true -> {}
    }
}
