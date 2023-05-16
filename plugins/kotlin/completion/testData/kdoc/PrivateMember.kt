// FIR_COMPARISON
// FIR_IDENTICAL
class A {
    private fun foo() {}
}

/**
 * [A.fo<caret>]
 */
fun test() {}

// EXIST: foo
// NOTHING_ELSE