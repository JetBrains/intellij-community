// "Add function to supertype…" "true"
// K2_ERROR: NOTHING_TO_OVERRIDE
open class A {
}
open class B : A() {
}
class C : B() {
    <caret>override fun f() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionToSupertypeFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddFunctionToSupertypeFix