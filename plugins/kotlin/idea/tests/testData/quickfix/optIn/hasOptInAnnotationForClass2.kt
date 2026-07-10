// "Opt in for 'B' on containing class 'C'" "true"
// K2_ERROR: OPT_IN_OVERRIDE_ERROR
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