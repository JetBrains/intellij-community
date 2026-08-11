// "Add 'open fun f(): Int' to 'A'" "true"
// WITH_STDLIB
// K2_ERROR: NOTHING_TO_OVERRIDE
open class A {
}
class B : A() {
    <caret>override fun f(): Int = 5
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionToSupertypeFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddFunctionToSupertypeFix