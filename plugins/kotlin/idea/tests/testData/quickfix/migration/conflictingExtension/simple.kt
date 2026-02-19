// "Delete redundant extension property" "true"
import java.io.File

val File.<caret>name: String
    get() = getName()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.ConflictingExtensionPropertyInspection$DeleteRedundantExtensionAction