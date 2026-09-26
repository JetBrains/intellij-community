// IS_APPLICABLE: false
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is String but Unit was expected
// K2_ERROR: RETURN_TYPE_MISMATCH

class A {
    public fun <caret>foo() {
        return ""
    }
}
