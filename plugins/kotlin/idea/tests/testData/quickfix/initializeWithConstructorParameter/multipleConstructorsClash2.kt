// K2_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT

// "Initialize with constructor parameter" "true"
class User(n: Int) {
    constructor(n: Int, s: String) : this(n)

    val userN<caret>ame: String
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix