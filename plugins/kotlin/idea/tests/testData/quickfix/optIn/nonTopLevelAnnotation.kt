// "Opt in for 'NestedMarker' on 'main'" "true"
// PRIORITY: HIGH
// K2_ERROR: OPT_IN_USAGE_ERROR

class TopClass {
    @RequiresOptIn
    annotation class NestedMarker
}

@TopClass.NestedMarker
class Main

fun main(){
    Main<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix