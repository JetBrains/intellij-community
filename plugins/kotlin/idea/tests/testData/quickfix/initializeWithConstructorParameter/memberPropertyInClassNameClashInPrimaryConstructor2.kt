// IGNORE_K1
// "Initialize with constructor parameter" "true"
val n: Int = 1

class A(m: Int = n) {
    <caret>var n: Int
        get() = 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix