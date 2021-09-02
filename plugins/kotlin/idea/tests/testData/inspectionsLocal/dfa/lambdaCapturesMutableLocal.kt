// PROBLEM: none
fun test() {
    var x = 10
    val fn = {
        if (<caret>x == 15) {

        }
    }
    x = 15
    fn()
}