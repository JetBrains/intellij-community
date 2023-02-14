// IS_APPLICABLE: false
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is String but Unit was expected

class A {
    public fun <caret>foo() {
        return ""
    }
}
