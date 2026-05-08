// "Opt in for 'AliasMarker' on 'AliasMarkerUsage'" "true"
// PRIORITY: HIGH
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Introduce import alias
// ACTION: Opt in for 'AliasMarker' in containing file 'typeAlias.kts'
// ACTION: Opt in for 'AliasMarker' in module 'light_idea_test_case'
// ACTION: Opt in for 'AliasMarker' on 'AliasMarkerUsage'
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@AliasMarker' or '@OptIn(AliasMarker::class)'

@RequiresOptIn
annotation class AliasMarker

@AliasMarker
class AliasTarget

typealias AliasMarkerUsage = <caret>AliasTarget

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix