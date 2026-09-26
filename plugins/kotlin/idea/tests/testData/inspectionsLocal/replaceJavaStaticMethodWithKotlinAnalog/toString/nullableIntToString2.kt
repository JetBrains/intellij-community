// WITH_STDLIB
// PROBLEM: none
// ERROR: Type mismatch: inferred type is Int? but Int was expected
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun foo() {
    val a: Int? = 5
    val t: String = Integer.<caret>toString(a, 5)
}
