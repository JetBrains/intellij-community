// "Make bar suspend" "true"
// K2_ERROR: ILLEGAL_SUSPEND_FUNCTION_CALL
// K2_ERROR: SUSPEND_OVERRIDDEN_BY_NON_SUSPEND

suspend fun foo() {}

open class A {
    open suspend fun bar() {}
}

class B : A() {
    override fun bar() {
        <caret>foo()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSuspendModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddSuspendModifierFixFactory$AddSuspendModifierFix