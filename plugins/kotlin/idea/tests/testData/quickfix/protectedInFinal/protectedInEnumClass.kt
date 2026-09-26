// "Make private" "true"
enum class C {
    X, Y, Z;
    <caret>protected fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase$MakePrivateFix