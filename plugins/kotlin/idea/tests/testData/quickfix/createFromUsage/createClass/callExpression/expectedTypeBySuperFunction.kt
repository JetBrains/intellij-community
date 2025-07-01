// "Create class 'XImpl'" "true"
// K2_AFTER_ERROR: Return type of 'f' is not a subtype of the return type of the overridden member 'fun f(): X' defined in 'A'.
interface X
interface A {
    fun f(): X
}

class B : A {
    override fun f() = <caret>XImpl()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction