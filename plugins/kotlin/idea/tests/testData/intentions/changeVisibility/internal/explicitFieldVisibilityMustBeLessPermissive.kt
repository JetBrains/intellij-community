// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// K2_ERROR: EXPLICIT_FIELD_VISIBILITY_MUST_BE_LESS_PERMISSIVE

class A {
    pr<caret>ivate val prop: List<Int>
        field = mutableListOf()
}

