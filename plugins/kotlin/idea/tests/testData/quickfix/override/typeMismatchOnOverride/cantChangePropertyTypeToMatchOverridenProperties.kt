// "class org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix" "false"
// ERROR: Type of 'x' doesn't match the type of the overridden var-property 'public abstract var x: String defined in A'
// K2_AFTER_ERROR: VAR_TYPE_MISMATCH_ON_OVERRIDE
// K2_ERROR: VAR_TYPE_MISMATCH_ON_OVERRIDE
interface A {
    var x: String
}

interface B {
    var x: Any
}

interface C : A, B {
    override var x: Int<caret>
}
