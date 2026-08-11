// "Create class 'XImpl'" "true"
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH_ON_OVERRIDE
// K2_ERROR: UNRESOLVED_REFERENCE
interface X
interface A {
    fun f(): X
}

class B : A {
    override fun f() = <caret>XImpl()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction