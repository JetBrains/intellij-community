// "Create enum 'Foo'" "true"
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE_WRONG_RECEIVER
class Test{
    fun doSth(){
        <caret>Foo::class.java
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction