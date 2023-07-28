// "Opt in for 'A' on 'root'" "true"
// ACTION: Add '-opt-in=HasOptInAnnotation3.A' to module light_idea_test_case compiler arguments
// ACTION: Opt in for 'A' in containing file 'hasOptInAnnotation3.kts'
// ACTION: Opt in for 'A' on 'root'
// ACTION: Opt in for 'A' on statement
// ACTION: Propagate 'A' opt-in requirement to 'root'
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class A

@A
fun f1() {}

@OptIn
fun root() {
    <caret>f1()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix