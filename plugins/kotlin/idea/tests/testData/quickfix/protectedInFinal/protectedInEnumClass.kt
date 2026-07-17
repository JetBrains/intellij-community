// "Make private" "true"
enum class C {
    X, Y, Z;
    <caret>protected fun foo() {}
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase$MakePrivateFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase$MakePrivateFix