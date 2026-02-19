// "Opt in for 'AliasMarker' on 'AliasMarkerUsage'" "true"
// PRIORITY: HIGH
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Add full qualifier
// ACTION: Introduce import alias
// ACTION: Opt in for 'AliasMarker' in containing file 'typeAlias.kts'
// ACTION: Opt in for 'AliasMarker' in module 'light_idea_test_case'
// ACTION: Opt in for 'AliasMarker' on 'AliasMarkerUsage'

@RequiresOptIn
annotation class AliasMarker

@AliasMarker
class AliasTarget

typealias AliasMarkerUsage = <caret>AliasTarget

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix