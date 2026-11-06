// IS_APPLICABLE: false
// K2_ERROR: SYNTAX

// See KT-7929: exception
class C {
    <caret>val (v1, v2) = Pair(1, 2)
}
