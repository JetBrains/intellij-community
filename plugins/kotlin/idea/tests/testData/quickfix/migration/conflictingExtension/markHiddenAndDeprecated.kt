// "Mark as '@Deprecated(..., level = DeprecationLevel.HIDDEN)'" "true"
import java.io.File

val File.<caret>name: String
    get() = getName()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.ConflictingExtensionPropertyInspection$MarkHiddenAndDeprecatedAction