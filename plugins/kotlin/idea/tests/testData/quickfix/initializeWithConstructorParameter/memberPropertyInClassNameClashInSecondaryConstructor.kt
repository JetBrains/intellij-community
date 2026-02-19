// "Initialize with constructor parameter" "true"
open class A {
    <caret>val n: Int

    constructor(n: Int)
}

class B : A(1)

fun test() {
    val a = A(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix