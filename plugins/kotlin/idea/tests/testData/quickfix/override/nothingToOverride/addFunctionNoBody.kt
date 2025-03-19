// "Add 'abstract fun f()' to 'A'" "true"
interface A
class B : A {
    <caret>override fun f() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionToSupertypeFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddFunctionToSupertypeFix