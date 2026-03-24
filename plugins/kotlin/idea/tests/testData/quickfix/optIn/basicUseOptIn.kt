// "Opt in for 'MyExperimentalAPI' on 'bar'" "true"
// PRIORITY: HIGH
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@a.b.MyExperimentalAPI' or '@OptIn(a.b.MyExperimentalAPI::class)'
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@a.b.MyExperimentalAPI' or '@OptIn(a.b.MyExperimentalAPI::class)'

package a.b

@RequiresOptIn
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class MyExperimentalAPI

@MyExperimentalAPI
class Some {
    @MyExperimentalAPI
    fun foo() {}
}

class Bar {
    fun bar() {
        Some().foo<caret>()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix