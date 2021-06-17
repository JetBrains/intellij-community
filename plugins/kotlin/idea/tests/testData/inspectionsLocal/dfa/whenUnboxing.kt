// PROBLEM: none
fun test(obj : Boolean?) {
    when(obj) {
        <caret>true -> {}
        false -> {}
        null -> {}
    }
}
