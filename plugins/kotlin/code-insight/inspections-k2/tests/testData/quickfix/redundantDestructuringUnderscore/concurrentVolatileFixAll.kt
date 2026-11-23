// "Fix all 'Redundant underscore entry in name-based destructuring' problems in file" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.RedundantDestructuringUnderscoreInspection
// PRIORITY: HIGH
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.actions.CleanupInspectionIntention

class Plain(val n: Int, val keep: Int) {
    @kotlin.concurrent.Volatile var title: Int = 0
}

fun test(x: Plain) {
    (val _ = title, val <caret>_ = n, val keep) = x
}
