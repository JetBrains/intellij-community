// "Opt in for 'AliasMarker' on 'AliasMarkerUsage'" "true"
// PRIORITY: HIGH


@RequiresOptIn
annotation class AliasMarker

@AliasMarker
class AliasTarget

typealias AliasMarkerUsage = <caret>AliasTarget

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix