// "Opt in for 'AliasMarker' on 'AliasMarkerUsage'" "true"
// PRIORITY: HIGH
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@AliasMarker' or '@OptIn(AliasMarker::class)'


@RequiresOptIn
annotation class AliasMarker

@AliasMarker
class AliasTarget

typealias AliasMarkerUsage = <caret>AliasTarget

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix