// "Add initializer" "true"
// K2_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT
class Foo {
    <caret>var foo: Int
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$AddInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializePropertyModCommandAction