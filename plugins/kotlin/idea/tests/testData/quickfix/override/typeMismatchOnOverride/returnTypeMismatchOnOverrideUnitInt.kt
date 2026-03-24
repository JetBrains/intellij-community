// "Remove explicitly specified return type" "true"
// K2_ERROR: Return type of 'fun remove(): Int' is not a subtype of the return type of the overridden member 'fun remove(): Unit' defined in 'A'.
abstract class A : java.util.Iterator<Int> {
    public abstract override fun remove() : Int<caret>;
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix