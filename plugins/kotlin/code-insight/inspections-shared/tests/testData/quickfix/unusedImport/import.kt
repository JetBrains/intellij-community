// "Remove unused imports" "true"

import SomeUnknownNamespace.SomeClass
import Top<caret>Level.NestedUnused1
import Top<caret>Level.NestedUnused2
import TopLevel.NestedUsed

class TopLevel {
    class NestedUnused1
    class NestedUnused2
    class NestedUsed
}

fun main() {
    NestedUsed()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.KotlinUnusedImportInspection$RemoveAllUnusedImportsFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.KotlinUnusedImportInspection$RemoveAllUnusedImportsFix