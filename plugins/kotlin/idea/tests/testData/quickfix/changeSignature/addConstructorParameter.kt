// "Add parameter to constructor 'Base'" "true"
// DISABLE-ERRORS

open class Base(var x: Int) {
    val y = Base(1, 2);

    fun f() {
        val base = Base(1);
    }
}

open class Inherited(x: Int) : Base(1, <caret>2.5) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$applicator$1