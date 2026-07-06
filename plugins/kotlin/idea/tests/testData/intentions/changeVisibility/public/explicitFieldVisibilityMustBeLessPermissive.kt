// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// K2_ERROR: EXPLICIT_FIELD_VISIBILITY_MUST_BE_LESS_PERMISSIVE

class A {
    pri<caret>vate val prop: List<Int>
        field = mutableListOf()
}

