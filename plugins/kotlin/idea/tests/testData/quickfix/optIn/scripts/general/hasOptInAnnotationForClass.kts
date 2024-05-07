// "Opt in for 'B' on containing class 'C'" "true"
// ACTION: Opt in for 'B' in containing file 'hasOptInAnnotationForClass.kts'
// ACTION: Opt in for 'B' in module 'light_idea_test_case'
// ACTION: Opt in for 'B' on 'someFun'
// ACTION: Opt in for 'B' on containing class 'C'
// ACTION: Opt in for 'B' on statement
// ACTION: Propagate 'B' opt-in requirement to 'someFun'
// ACTION: Propagate 'B' opt-in requirement to containing class 'C'
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@A
fun f1() = Unit

@B
fun f2() = Unit

@OptIn(A::class)
class C {
    fun someFun() {
        f1()
        <caret>f2()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix