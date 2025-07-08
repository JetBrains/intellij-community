// "class org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix" "false"
// ERROR: Type of 'x' doesn't match the type of the overridden var-property 'public abstract var x: String defined in A'
// K2_AFTER_ERROR: Type of 'x' doesn't match the type of the overridden 'var' property 'var x: String' defined in 'A'.
interface A {
    var x: String
}

interface B {
    var x: Any
}

interface C : A, B {
    override var x: Int<caret>
}
