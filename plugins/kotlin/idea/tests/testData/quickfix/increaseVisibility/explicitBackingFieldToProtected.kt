// "Make 'prop' protected" "true"
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// K2_ERROR: EXPLICIT_FIELD_VISIBILITY_MUST_BE_LESS_PERMISSIVE

open class Point {
    pri<caret>vate val prop: List<Int>
        field = mutableListOf()
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToProtectedModCommandAction