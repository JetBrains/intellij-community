// "Make 'prop' internal" "true"
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

class Point {
    pri<caret>vate val prop: List<Int>
        field = mutableListOf()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToInternalModCommandAction