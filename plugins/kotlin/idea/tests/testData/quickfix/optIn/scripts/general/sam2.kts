// "Opt in for 'MyOptIn' on 'bar'" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Add '-opt-in=Sam2.MyOptIn' to module light_idea_test_case compiler arguments
// ACTION: Do not show implicit receiver and parameter hints
// ACTION: Opt in for 'MyOptIn' in containing file 'sam2.kts'
// ACTION: Opt in for 'MyOptIn' on 'bar'
// ACTION: Opt in for 'MyOptIn' on statement
// ACTION: Propagate 'MyOptIn' opt-in requirement to 'bar'

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
fun foo() {
}

fun interface SamI {
    fun run()
}

val bar = SamI {
    foo<caret>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix