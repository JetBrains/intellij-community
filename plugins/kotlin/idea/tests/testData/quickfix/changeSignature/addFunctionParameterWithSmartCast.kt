// "Add parameter to function 'fooFun'" "true"
// K2_ERROR: Too many arguments for 'fun fooFun(): Int'.
sealed class Foo {
    class SubFoo : Foo()
    class Sub2Foo : Foo()
}

fun fooFun(): Int = 1

val subFoo: Foo = Foo.SubFoo()
val bar = when(subFoo){
    is Foo.SubFoo -> fooFun(<caret>subFoo)
    else -> 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix