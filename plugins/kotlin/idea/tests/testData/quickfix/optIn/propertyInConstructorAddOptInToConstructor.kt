// "Opt in for 'PropertyTypeMarker' on constructor" "true"
// PRIORITY: HIGH
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@PropertyTypeMarker' or '@OptIn(PropertyTypeMarker::class)'


@RequiresOptIn
annotation class PropertyTypeMarker

@PropertyTypeMarker
class PropertyTypeMarked

class PropertyTypeContainer(val subject: Property<caret>TypeMarked)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix