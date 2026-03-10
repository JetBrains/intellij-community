// "Make 'prop' protected" "true"
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// K2_ERROR: Private properties cannot have explicit backing fields.

open class Point {
    pri<caret>vate val prop: List<Int>
        field = mutableListOf()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToProtectedModCommandAction