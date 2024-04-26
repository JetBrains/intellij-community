// "Safe delete 'property'" "true"
class UnusedProperty() {
    val <caret>property: String = ":)"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix