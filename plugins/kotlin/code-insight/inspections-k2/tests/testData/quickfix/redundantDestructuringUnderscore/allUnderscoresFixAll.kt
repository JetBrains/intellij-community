// "Fix all 'Redundant underscore entry in name-based destructuring' problems in file" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.RedundantDestructuringUnderscoreInspection

class Plain(val a: String, val b: Int, val c: Boolean = false)

fun test() {
    (val <caret>_ = a, val _ = b) = Plain("", 1)
    (val _ = a, val _ = b, val _ = c) = Plain("", 1)
}
