// IS_APPLICABLE: false
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is String but Unit was expected
// K2_ERROR: Return type mismatch: expected 'Unit', actual 'String'.

class A {
    public fun <caret>foo() {
        return ""
    }
}
