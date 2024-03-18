// "Opt in for 'MyOptIn' on 'SamI'" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Add '-opt-in=Sam.MyOptIn' to module light_idea_test_case compiler arguments
// ACTION: Opt in for 'MyOptIn' in containing file 'sam.kts'
// ACTION: Opt in for 'MyOptIn' on 'SamI'
// ACTION: Opt in for 'MyOptIn' on statement

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

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix