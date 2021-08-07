// PROBLEM: none
fun test(v : Any?) {
    val b = (v as? String)
    if (<caret>v == null) {

    }
}