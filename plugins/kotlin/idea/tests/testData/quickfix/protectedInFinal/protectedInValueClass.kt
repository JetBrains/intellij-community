// "Make private" "true"
@JvmInline
value class C(val x: Int) {
    <caret>protected fun foo() {}
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase$MakePrivateFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase$MakePrivateFix