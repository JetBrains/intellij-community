// PROBLEM: Condition is always true
// FIX: none
fun test(x : Int) {
    val data = "Value = ${x}"
    if (<caret>data.length > 5) {

    }
}