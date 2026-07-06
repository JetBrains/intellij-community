// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

class A

fun test(a: A) {
    val t: Int = a.<caret>foo
}