// "Delete redundant extension property" "true"

var Thread.<caret>priority: Int
    get() = this.getPriority()
    set(value) {
        this.setPriority(value)
    }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.ConflictingExtensionPropertyInspection$DeleteRedundantExtensionAction