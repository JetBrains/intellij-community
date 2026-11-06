// "Fix all 'Non-canonical modifier order' problems in file" "true"
// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.SortModifiersInspection
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.actions.CleanupInspectionIntention
class Column

public companion <caret>inline fun Column.bar(): Int = 5

class Sample {
    companion private object
}

abstract public class Ordinary
