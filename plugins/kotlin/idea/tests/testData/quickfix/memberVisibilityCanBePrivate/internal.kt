// "Make 'a' 'private'" "true"
class A {
    <caret>internal val a = ""

    fun foo() {
        a
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.K2MemberVisibilityCanBePrivateInspection$AddPrivateModifierFix