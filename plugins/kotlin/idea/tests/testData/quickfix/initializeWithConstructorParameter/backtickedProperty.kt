// "Initialize with constructor parameter" "true"
// K2_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT

class A {
    <caret>val `foo bar`: Int

    constructor()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix
