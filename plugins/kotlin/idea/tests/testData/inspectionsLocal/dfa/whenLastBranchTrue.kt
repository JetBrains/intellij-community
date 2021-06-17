// PROBLEM: none
fun test(obj : Boolean) {
    when(obj) {
        true -> {}
        <caret>false -> {}
    }
}
class X {}
class Y {}