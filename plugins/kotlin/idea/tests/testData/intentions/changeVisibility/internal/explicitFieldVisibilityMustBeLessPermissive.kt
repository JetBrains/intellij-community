// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// K2_ERROR: Private properties cannot have explicit backing fields.

class A {
    pr<caret>ivate val prop: List<Int>
        field = mutableListOf()
}

// IGNORE_K1