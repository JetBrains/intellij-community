// PROBLEM: none
fun foo() {
    val j = J()
    // no problem as comment does not follow pattern "paremeterName ="
    j.foo(<caret>/* 1st argument */ 1, "a")
}