// "Fix all 'Redundant underscore entry in name-based destructuring' problems in file" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.RedundantDestructuringUnderscoreInspection

class Plain(val n: Int) {
    val title: String get() = error("side effect")
}

fun test(x: Plain) {
    (val <caret>_ = title, val n) = x
}