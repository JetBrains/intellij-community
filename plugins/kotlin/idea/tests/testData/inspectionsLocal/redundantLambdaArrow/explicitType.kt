// PROBLEM: none
fun takeP(a: Any) {}
val z = takeP { it: Int<caret> -> it.div(2) }