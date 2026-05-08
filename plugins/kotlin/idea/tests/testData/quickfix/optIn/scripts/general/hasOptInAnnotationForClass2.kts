// "Opt in for 'B' on containing class 'C'" "true"
// ACTION: Go To Super Method
// ACTION: Move to top level
// ACTION: Opt in for 'B' in containing file 'hasOptInAnnotationForClass2.kts'
// ACTION: Opt in for 'B' in module 'light_idea_test_case'
// ACTION: Opt in for 'B' on 'bar'
// ACTION: Opt in for 'B' on containing class 'C'
// ACTION: Propagate 'B' opt-in requirement to 'bar'
// ACTION: Propagate 'B' opt-in requirement to containing class 'C'
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: Base declaration of supertype 'I' needs opt-in. The declaration override must be annotated with '@B' or '@OptIn(B::class)'
@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

interface I {
    @A
    fun foo(): Unit

    @B
    fun bar(): Unit
}

@OptIn(A::class)
class C : I {
    override fun foo() {}
    override fun <caret>bar() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$ModifyOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$ModifyOptInAnnotationFix