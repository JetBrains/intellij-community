// "Initialize with constructor parameter" "true"
open class A(n: Int) {
    <caret>var n: Int
        get() = 1
}

class B : A(0)

fun test() {
    val a = A(0)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix