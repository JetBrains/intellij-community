// "Opt in for 'NestedMarker' on 'main'" "true"
// PRIORITY: HIGH
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@foo.TopClass.NestedMarker' or '@OptIn(foo.TopClass.NestedMarker::class)'

package foo

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