// "Make class open" "true"
final class C {
    <caret>protected fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase$MakeOpenFix