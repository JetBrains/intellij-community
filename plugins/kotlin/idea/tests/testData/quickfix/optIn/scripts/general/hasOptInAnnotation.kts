// "Opt in for 'B' on 'root'" "true"
// PRIORITY: HIGH
// ACTION: Opt in for 'B' in containing file 'hasOptInAnnotation.kts'
// ACTION: Opt in for 'B' in module 'light_idea_test_case'
// ACTION: Opt in for 'B' on 'root'
// ACTION: Opt in for 'B' on statement
// ACTION: Propagate 'B' opt-in requirement to 'root'
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@A
fun f1() {}

@B
fun f2() {}

@OptIn(A::class)
fun root() {
    f1()
    <caret>f2()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$ModifyOptInAnnotationFix