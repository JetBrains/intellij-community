// "Remove explicitly specified return type" "true"
abstract class A : java.util.Iterator<Int> {
    public abstract override fun remove() : Int<caret>;
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix