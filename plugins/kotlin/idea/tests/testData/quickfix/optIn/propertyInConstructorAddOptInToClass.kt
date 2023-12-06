// "Opt in for 'PropertyTypeMarker' on containing class 'PropertyTypeContainer'" "true"

@RequiresOptIn
annotation class PropertyTypeMarker

@PropertyTypeMarker
class PropertyTypeMarked

class PropertyTypeContainer(val subject: Property<caret>TypeMarked)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix