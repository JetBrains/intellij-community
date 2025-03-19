// IGNORE_K1
// "Initialize with constructor parameter" "true"
class User(n: Int) {
    constructor(n: Int, s: String) : this(n)

    val userN<caret>ame: String
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix