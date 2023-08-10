// "Delete redundant extension property" "true"

val Thread.<caret>priority: Int
    get() = getPriority()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.ConflictingExtensionPropertyInspection$DeleteRedundantExtensionAction