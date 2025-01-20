// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: Argument type mismatch: actual type is 'Int?', but 'Int' was expected.
// ERROR: Type mismatch: inferred type is Int? but Int was expected
fun foo() {
    val a: Int? = 5
    val t: String = Integer.<caret>toString(a, 5)
}
