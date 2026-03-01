// "class org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix" "false"
// ERROR: Type of 'x' doesn't match the type of the overridden var-property 'public abstract var x: Int defined in A'
// K2_AFTER_ERROR: Type of 'var x: String' doesn't match the type of the overridden 'var' property 'var x: Int' defined in 'A'.
interface A {
    var x: Int
}

interface B {
    var x: Any
}

interface C : A, B {
    override var x: String<caret>
}
