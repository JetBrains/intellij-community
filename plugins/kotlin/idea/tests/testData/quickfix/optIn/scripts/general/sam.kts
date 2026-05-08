// "Opt in for 'MyOptIn' on 'SamI'" "true"
// PRIORITY: HIGH
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Convert lambda to reference
// ACTION: Introduce import alias
// ACTION: Opt in for 'MyOptIn' in containing file 'sam.kts'
// ACTION: Opt in for 'MyOptIn' in module 'light_idea_test_case'
// ACTION: Opt in for 'MyOptIn' on 'SamI'
// ACTION: Opt in for 'MyOptIn' on statement
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyOptIn' or '@OptIn(MyOptIn::class)'

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
fun foo() {
}

fun interface SamI {
    fun run()
}

SamI {
    foo<caret>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
