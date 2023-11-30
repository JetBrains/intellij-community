// "Opt in for 'NestedMarker' on 'main'" "true"

class TopClass {
    @RequiresOptIn
    annotation class NestedMarker
}

@TopClass.NestedMarker
class Main

fun main(){
    Main<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix