// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// K2_ERROR: Private properties cannot have explicit backing fields.

class A {
    pri<caret>vate val prop: List<Int>
        field = mutableListOf()
}

// IGNORE_K1