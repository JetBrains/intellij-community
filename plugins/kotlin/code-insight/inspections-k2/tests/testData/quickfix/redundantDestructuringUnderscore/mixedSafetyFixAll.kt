// "Fix all 'Redundant underscore entry in name-based destructuring' problems in file" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.RedundantDestructuringUnderscoreInspection
// PRIORITY: HIGH
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.actions.CleanupInspectionIntention

class Plain(val a: String, val b: Int) {
    val c: Int get() = error("side effect")
}

fun test() {
    (val _ = c, val <caret>_ = a, val _ = b) = Plain("", 1)
    (val _ = a, val _ = b, val _ = c) = Plain("", 1)
}
